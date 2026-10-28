package net.reichholf.dreamdroid.ui.setup

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CompletableDeferred
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
import net.reichholf.dreamdroid.data.BackupRepository
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.data.ReceiverProfileCheckRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.GenericSetting
import net.reichholf.dreamdroid.testutil.MemoryBackupDocuments
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [SetupAssistantViewModel] over the real profile and check repositories, with a
 * [MockWebServer] receiver. Discovery is a fake: nothing on the JVM answers mDNS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupAssistantViewModelTest {
    private val server = MockWebServer()
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val preferences = PreferenceManager.getDefaultSharedPreferences(testProfiles.context)
    private val backups = BackupRepository(profiles, SettingsRepository(preferences))
    private val documents = MemoryBackupDocuments()
    private val viewModels = mutableListOf<ViewModel>()
    private var searches = 0

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun searchRunsOnceAndListsReceivers() = runTest {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.search()
        viewModel.search()
        val state = viewModel.uiState.first { it.searched }

        assertEquals(listOf(SetupReceiver("dm920", "10.0.0.9", 80)), state.devices)
        assertFalse(state.searching)
        assertEquals(1, searches)
    }

    @Test
    fun checkReachesTheReceiverAndSaveActivatesTheProfile() = runTest {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))
        val viewModel = viewModel(SavedStateHandle())
        viewModel.advance()
        type(viewModel.host, server.hostName)
        viewModel.advance()
        type(viewModel.port, server.port.toString())
        viewModel.advance()

        viewModel.advance()
        val checked = viewModel.uiState.first { it.checkResult != null }

        assertFalse(checked.checkResult!!.hasError)
        assertEquals("/web/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
        viewModel.advance()
        assertEquals(SetupStep.Name, viewModel.uiState.value.draft.step)
        assertEquals(server.hostName, viewModel.profileName.text)

        viewModel.advance()
        val finished = viewModel.uiState.first { it.finished }

        val saved = profiles.profiles().single()
        assertEquals(server.hostName, saved.host)
        assertEquals(server.port, saved.port)
        assertEquals(saved.id, profiles.current.value!!.id)
        assertEquals(SetupDraft(), finished.draft)
        assertEquals("", viewModel.host.text)
        assertEquals("80", viewModel.port.text)
        viewModel.onFinishHandled()
        assertFalse(viewModel.uiState.value.finished)
    }

    @Test
    fun notAReceiverIsAnError() = runTest {
        // An HTTP error resolves its text through Resources, which the JVM only stubs.
        server.enqueue(MockResponse().setBody("<html>not a receiver</html>"))
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.host, server.hostName)
        type(viewModel.port, server.port.toString())

        viewModel.check()
        val result = viewModel.uiState.first { it.checkResult != null }.checkResult!!

        assertTrue(result.hasError)
        assertTrue(profiles.profiles().isEmpty())
    }

    @Test
    fun advanceDuringARunningCheckDoesNothing() = runTest {
        val gate = CompletableDeferred<ProfileCheckResult>()
        var checks = 0
        val viewModel = viewModel(
            SavedStateHandle(),
            object : ProfileCheckRepository {
                override suspend fun check(profile: Profile): ProfileCheckResult {
                    checks++
                    return gate.await()
                }

                override suspend fun checkReusingDeviceInfo(profile: Profile) = check(profile)
            }
        )
        viewModel.advance()
        type(viewModel.host, "10.0.0.7")
        viewModel.advance()
        viewModel.advance()
        viewModel.advance()
        assertTrue(viewModel.uiState.value.checking)

        viewModel.advance()

        assertEquals(1, checks)
        assertTrue(viewModel.uiState.value.checking)
        assertEquals(SetupStep.SignIn, viewModel.uiState.value.draft.step)
        gate.complete(ProfileCheckResult())
        val checked = viewModel.uiState.first { it.checkResult != null }
        assertFalse(checked.checking)
        assertEquals(SetupStep.SignIn, checked.draft.step)
        assertEquals(1, checks)
    }

    @Test
    fun editingTheDraftDropsTheCheckResult() = runTest {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.host, server.hostName)
        type(viewModel.port, server.port.toString())
        viewModel.check()
        viewModel.uiState.first { it.checkResult != null }

        type(viewModel.user, "admin")

        assertEquals(null, viewModel.uiState.value.checkResult)
        assertFalse(viewModel.uiState.value.checking)
    }

    @Test
    fun draftAndTypedTextSurviveInSavedState() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.advance()
        type(first.host, "10.0.0.7")
        first.onHttpsChange(true)
        type(first.user, "admin")

        val restored = viewModel(handle)

        val draft = restored.uiState.value.draft
        assertEquals(SetupStep.Find, draft.step)
        assertTrue(draft.useHttps)
        assertEquals("10.0.0.7", restored.host.text)
        assertEquals("443", restored.port.text)
        assertEquals("admin", restored.user.text)
        assertEquals("dreambox", restored.pass.text)
    }

    @Test
    fun pickingAReceiverIsNotATypedEdit() {
        val viewModel = viewModel(SavedStateHandle())
        viewModel.advance()

        viewModel.onPick(SetupReceiver("dm920", "10.0.0.9", 443))
        Snapshot.sendApplyNotifications()

        assertEquals("dm920", viewModel.uiState.value.draft.suggestedName)
        assertEquals("443", viewModel.port.text)
        type(viewModel.host, "10.0.0.10")
        assertEquals("", viewModel.uiState.value.draft.suggestedName)
    }

    @Test
    fun typingANameKeepsItOverTheSuggestion() {
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.profileName, "Living room")

        assertTrue(viewModel.uiState.value.draft.nameEdited)
    }

    @Test
    fun restoreImportsTheFilesProfilesAndFinishes() = runTest {
        documents.files[RESTORE_URI] = backupJson(
            Profile.getDefault().apply {
                name = "Living room"
                host = "10.0.0.5"
                pass = "secret"
            }
        )
        val viewModel = viewModel(SavedStateHandle())

        viewModel.restoreFrom(RESTORE_URI)
        val step = viewModel.uiState.first { it.draft.step == SetupStep.Import }

        assertFalse(step.restoring)
        assertEquals(null, step.restoreError)
        assertEquals(listOf("Living room"), viewModel.importReview.value!!.profiles.map { it.name })

        viewModel.advance()
        val finished = viewModel.uiState.first { it.finished }

        val saved = profiles.profiles().single()
        assertEquals(SetupDraft(), finished.draft)
        assertEquals(saved.id, profiles.current.value!!.id)
        assertEquals("secret", saved.pass)
        assertEquals(null, viewModel.importReview.value)
    }

    @Test
    fun restoreTakesOnlyTheChosenProfiles() = runTest {
        documents.files[RESTORE_URI] = backupJson(
            Profile.getDefault().apply {
                name = "Kitchen"
                host = "10.0.0.3"
            },
            Profile.getDefault().apply {
                name = "Cellar"
                host = "10.0.0.4"
            }
        )
        val viewModel = viewModel(SavedStateHandle())
        viewModel.restoreFrom(RESTORE_URI)
        viewModel.uiState.first { it.draft.step == SetupStep.Import }

        viewModel.setImportProfileChecked(1, false)
        viewModel.advance()
        viewModel.uiState.first { it.finished }

        assertEquals(listOf("Kitchen"), profiles.profiles().map { it.name })
    }

    @Test
    fun settingsOnlyRestoreIsNotStarted() = runTest {
        documents.files[RESTORE_URI] = GsonBuilder().create().toJson(
            BackupData().apply {
                addProfile(
                    Profile.getDefault().apply {
                        name = "Kitchen"
                        host = "10.0.0.3"
                    }
                )
                addGenericSetting(
                    GenericSetting(DreamDroid.PREFS_KEY_INSTANT_ZAP, "true", "Boolean")
                )
            }
        )
        val viewModel = viewModel(SavedStateHandle())
        viewModel.restoreFrom(RESTORE_URI)
        viewModel.uiState.first { it.draft.step == SetupStep.Import }

        viewModel.setImportProfileChecked(0, false)
        viewModel.advance()

        // The action is disabled without a profile, so the restore must not run: no error on
        // Welcome, and the settings stay untouched.
        assertEquals(SetupStep.Import, viewModel.uiState.value.draft.step)
        assertEquals(null, viewModel.uiState.value.restoreError)
        assertFalse(viewModel.uiState.value.restoring)
        assertFalse(viewModel.uiState.value.finished)
        assertTrue(profiles.profiles().isEmpty())
        assertEquals(null, profiles.current.value)
        assertFalse(preferences.getBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, false))
    }

    @Test
    fun aBackupWithoutProfilesStaysOnWelcome() = runTest {
        documents.files[RESTORE_URI] = GsonBuilder().create().toJson(
            BackupData().apply {
                addGenericSetting(
                    GenericSetting(DreamDroid.PREFS_KEY_INSTANT_ZAP, "true", "Boolean")
                )
            }
        )
        val viewModel = viewModel(SavedStateHandle())

        viewModel.restoreFrom(RESTORE_URI)
        val state = viewModel.uiState.first { it.restoreError != null }

        assertEquals(UiText.Resource(R.string.setup_restore_no_profiles), state.restoreError)
        assertEquals(SetupStep.Welcome, state.draft.step)
        assertEquals(null, viewModel.importReview.value)
        assertTrue(profiles.profiles().isEmpty())
    }

    @Test
    fun anUnreadableBackupSaysSo() = runTest {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.restoreFrom("content://missing")
        val state = viewModel.uiState.first { it.restoreError != null }

        assertEquals(UiText.Resource(R.string.setup_restore_failed), state.restoreError)
        assertEquals(SetupStep.Welcome, state.draft.step)
        assertFalse(state.restoring)
    }

    @Test
    fun theRestoreReviewSurvivesProcessDeath() = runTest {
        documents.files[RESTORE_URI] = backupJson(
            Profile.getDefault().apply {
                name = "Kitchen"
                host = "10.0.0.3"
                pass = "secret"
            }
        )
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.restoreFrom(RESTORE_URI)
        first.uiState.first { it.draft.step == SetupStep.Import }
        first.setImportPasswords(false)
        documents.files.clear()

        val restored = viewModel(handle)
        val review = restored.importReview.first { it != null }!!

        assertEquals(SetupStep.Import, restored.uiState.value.draft.step)
        assertFalse(review.includePasswords)
    }

    /** Types into [field] the way the text field does, then lets observers see it. */
    private fun type(field: SavedTextField, text: String) {
        field.state.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun backupJson(vararg rows: Profile): String =
        GsonBuilder().create().toJson(BackupData().apply { rows.forEach(::addProfile) })

    private fun viewModel(
        handle: SavedStateHandle,
        checks: ProfileCheckRepository = ReceiverProfileCheckRepository(
            profiles,
            receiverApis(profiles, testProfiles.context, testProfiles.capabilities),
            testProfiles.capabilities
        )
    ) = SetupAssistantViewModel(
        handle,
        profiles,
        checks,
        ReceiverDiscovery {
            searches++
            listOf(
                Profile.getDefault().apply {
                    name = "dm920"
                    host = "10.0.0.9"
                    port = 80
                }
            )
        },
        backups,
        documents
    ).also { viewModels += it }

    private companion object {
        const val RESTORE_URI = "content://documents/backup.json"
    }
}
