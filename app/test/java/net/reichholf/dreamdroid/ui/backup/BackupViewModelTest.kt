package net.reichholf.dreamdroid.ui.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BackupDocuments
import net.reichholf.dreamdroid.data.BackupRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.GenericSetting
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [BackupViewModel] over the real repositories; documents are strings in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val preferences = PreferenceManager.getDefaultSharedPreferences(testProfiles.context)
    private val backups = BackupRepository(profiles, SettingsRepository(preferences))
    private val documents = MemoryDocuments()
    private val viewModels = mutableListOf<ViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    @Test
    fun listsProfilesAndMarksTheCurrentOne() = runTest {
        val living = saved("Living Room", "10.0.0.1")
        saved("Bedroom", "10.0.0.2")
        profiles.setCurrent(living.id!!)

        val state = viewModel().uiState.first { it.profiles.size == 2 }

        assertEquals(listOf("Living Room", "Bedroom"), state.profiles.map { it.name })
        assertEquals(listOf(true, false), state.profiles.map { it.current })
        assertTrue(state.profiles.all { it.checked })
        assertFalse(state.exportSettings)
        assertTrue(state.includePasswords)
        assertEquals(UiText.Resource(R.string.backup), state.title)
    }

    @Test
    fun exportWritesTheChosenProfilesWithoutSettingsByDefault() = runTest {
        saved("Living Room", "10.0.0.1")
        val bedroom = saved("Bedroom", "10.0.0.2")
        preferences.edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "0").apply()
        val viewModel = viewModel()
        viewModel.uiState.first { it.profiles.size == 2 }

        viewModel.setProfileChecked(bedroom.id!!, false)
        viewModel.exportTo(URI)
        val state = viewModel.uiState.first { it.userMessage != null }

        assertEquals(UiText.Resource(R.string.backup_export_successful), state.userMessage)
        val json = documents.files.getValue(URI)
        assertEquals(listOf("Living Room"), read(json).profiles.map { it.name })
        assertFalse(json.contains("mSettings"))
    }

    @Test
    fun exportWithSettingsAndWithoutPasswords() = runTest {
        saved("Living Room", "10.0.0.1", pass = "secret")
        preferences.edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "0").apply()
        val viewModel = viewModel()
        viewModel.uiState.first { it.profiles.size == 1 }

        viewModel.setExportSettings(true)
        viewModel.setIncludePasswords(false)
        viewModel.exportTo(URI)
        viewModel.uiState.first { it.userMessage != null }

        val exported = read(documents.files.getValue(URI))
        assertEquals("", exported.profiles.single().pass)
        assertEquals(false, exported.passwordsIncluded)
        assertTrue(
            exported.settings.orEmpty().any {
                it.key == DreamDroid.PREFS_KEY_THEME_TYPE && it.value == "0"
            }
        )
    }

    @Test
    fun failedExportSaysSo() = runTest {
        documents.writable = false
        val viewModel = viewModel()

        viewModel.exportTo(URI)
        val state = viewModel.uiState.first { it.userMessage != null }

        assertEquals(UiText.Resource(R.string.backup_export_missing_permission), state.userMessage)
    }

    @Test
    fun importStoresProfilesAndSettingsAndListsThem() = runTest {
        val data = BackupData()
        data.addProfile(receiver("Kitchen", "10.0.0.3"))
        data.addGenericSetting(GenericSetting(DreamDroid.PREFS_KEY_INSTANT_ZAP, "true", "Boolean"))
        documents.files[URI] = GsonBuilder().create().toJson(data)
        val viewModel = viewModel()

        viewModel.importFrom(URI)
        val state = viewModel.uiState.first { it.profiles.isNotEmpty() }

        assertEquals(UiText.Resource(R.string.backup_import_successful), state.userMessage)
        assertEquals(listOf("Kitchen"), state.profiles.map { it.name })
        assertTrue(preferences.getBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, false))
    }

    @Test
    fun unreadableOrInvalidImportSaysSo() = runTest {
        documents.files[URI] = "{"
        val viewModel = viewModel()

        viewModel.importFrom(URI)
        assertEquals(
            UiText.Resource(R.string.backup_import_error),
            viewModel.uiState.first { it.userMessage != null }.userMessage
        )
        viewModel.onMessageShown()
        viewModel.importFrom("content://missing")
        assertEquals(
            UiText.Resource(R.string.backup_import_error),
            viewModel.uiState.first { it.userMessage != null }.userMessage
        )
        assertTrue(profiles.profiles().isEmpty())
    }

    @Test
    fun missingPickerShowsTheSystemReasonOrAFallback() {
        val viewModel = viewModel()

        viewModel.onImportPickerMissing("No app")
        assertEquals(UiText.Raw("No app"), viewModel.uiState.value.userMessage)
        viewModel.onExportPickerMissing(null)
        assertEquals(
            UiText.Resource(R.string.backup_export_missing_permission),
            viewModel.uiState.value.userMessage
        )
    }

    @Test
    fun passwordWarningOpensAndCloses() {
        val viewModel = viewModel()

        viewModel.confirmPasswords()
        assertTrue(viewModel.uiState.value.confirmingPasswords)
        viewModel.dismissPasswordWarning()
        assertFalse(viewModel.uiState.value.confirmingPasswords)
    }

    @Test
    fun exportChoicesSurviveProcessDeath() = runTest {
        saved("Living Room", "10.0.0.1")
        val bedroom = saved("Bedroom", "10.0.0.2")
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.uiState.first { it.profiles.size == 2 }
        first.setProfileChecked(bedroom.id!!, false)
        first.setExportSettings(true)
        first.setIncludePasswords(false)

        val state = viewModel(handle).uiState.first { it.profiles.size == 2 }

        assertEquals(listOf(true, false), state.profiles.map { it.checked })
        assertTrue(state.exportSettings)
        assertFalse(state.includePasswords)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): BackupViewModel =
        BackupViewModel(handle, backups, profiles, documents).also { viewModels += it }

    private fun saved(name: String, host: String, pass: String = ""): Profile =
        receiver(name, host).apply { this.pass = pass }.also { runBlocking { profiles.save(it) } }

    private fun receiver(name: String, host: String): Profile = Profile.getDefault().apply {
        this.name = name
        this.host = host
    }

    private fun read(json: String): BackupData =
        GsonBuilder().create().fromJson(json, BackupData::class.java)

    private class MemoryDocuments : BackupDocuments {
        val files = HashMap<String, String>()
        var writable = true

        override suspend fun read(uri: String): String? = files[uri]

        override suspend fun write(uri: String, text: String): Boolean {
            if (writable) {
                files[uri] = text
            }
            return writable
        }
    }

    private companion object {
        const val URI = "content://documents/backup.json"
    }
}
