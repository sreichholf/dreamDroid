package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
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
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.data.CacheRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.room.MovieLocationStripEntity
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [SettingsViewModel] over the real settings and cache repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val database = testProfiles.database
    private val preferences = PreferenceManager.getDefaultSharedPreferences(testProfiles.context)
    private val connection = SessionConnectionHolder()
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
    fun showsTheStoredSettings() {
        preferences.edit().putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, true).apply()

        val state = viewModel().uiState.value

        assertTrue(state.settings.picons)
        assertEquals(UiText.Resource(R.string.settings), state.title)
        assertNull(state.effect)
    }

    @Test
    fun updateStoresTheSettingAndShowsIt() {
        val viewModel = viewModel()

        viewModel.update { it.copy(nowPlayingStrip = false) }

        assertFalse(preferences.getBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, true))
        assertFalse(viewModel.uiState.value.settings.nowPlayingStrip)
        assertNull(viewModel.uiState.value.effect)
    }

    @Test
    fun aWriteFromElsewhereShowsUp() {
        val viewModel = viewModel()

        preferences.edit().putString(DreamDroid.PREFS_KEY_GRID_MAX_COLS, "4").apply()

        assertEquals("4", viewModel.uiState.value.settings.gridMaxCols)
    }

    @Test
    fun themeAndDynamicColorChangesAskTheActivity() {
        val viewModel = viewModel()

        viewModel.update { it.copy(themeType = "0") }
        assertEquals(SettingsEffect.ApplyTheme, viewModel.uiState.value.effect)
        viewModel.onEffectHandled()
        assertNull(viewModel.uiState.value.effect)

        viewModel.update { it.copy(themeType = "0") }
        assertNull(viewModel.uiState.value.effect)

        viewModel.update { it.copy(dynamicThemeColors = true) }
        assertEquals(SettingsEffect.Restart, viewModel.uiState.value.effect)
    }

    @Test
    fun syncPathDialogEditsAndStoresThePath() {
        val viewModel = viewModel()

        viewModel.editSyncPiconsPath()
        assertTrue(viewModel.uiState.value.editingSyncPiconsPath)
        assertEquals(AppSettings().syncPiconsPath, viewModel.syncPiconsPath.text)
        type(viewModel, "/media/hdd/picon")
        viewModel.confirmSyncPiconsPath()

        assertFalse(viewModel.uiState.value.editingSyncPiconsPath)
        assertEquals("/media/hdd/picon", viewModel.uiState.value.settings.syncPiconsPath)
        assertEquals(
            "/media/hdd/picon",
            preferences.getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, null)
        )
    }

    @Test
    fun dismissingTheSyncPathDialogKeepsThePath() {
        val viewModel = viewModel()

        viewModel.editSyncPiconsPath()
        type(viewModel, "/tmp")
        viewModel.dismissSyncPiconsPath()

        assertFalse(viewModel.uiState.value.editingSyncPiconsPath)
        assertEquals(AppSettings().syncPiconsPath, viewModel.uiState.value.settings.syncPiconsPath)
    }

    @Test
    fun openSyncPathDialogSurvivesProcessDeath() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.editSyncPiconsPath()
        type(first, "/media/usb")

        val restored = viewModel(handle)

        assertTrue(restored.uiState.value.editingSyncPiconsPath)
        assertEquals("/media/usb", restored.syncPiconsPath.text)
    }

    @Test
    fun resettingTheCurrentProfileLeavesOtherProfilesCached() = runTest {
        val living = saved("Living Room")
        val bedroom = saved("Bedroom")
        profiles.setCurrent(living.id!!)
        cache(living.id!!)
        cache(bedroom.id!!)
        goOffline()
        val viewModel = viewModel()

        viewModel.resetCache(allProfiles = false)
        val state = viewModel.uiState.first { it.userMessage != null }

        assertEquals(UiText.Resource(R.string.reset_cache_done), state.userMessage)
        assertEquals(0, database.movieDao().locationMetaCount(living.id!!))
        assertEquals(1, database.movieDao().locationMetaCount(bedroom.id!!))
        assertNull(connection.status.value.session)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun resettingAllProfilesClearsEveryCache() = runTest {
        val living = saved("Living Room")
        val bedroom = saved("Bedroom")
        profiles.setCurrent(living.id!!)
        cache(living.id!!)
        cache(bedroom.id!!)
        val viewModel = viewModel()

        viewModel.resetCache(allProfiles = true)
        viewModel.uiState.first { it.userMessage != null }

        assertEquals(0, database.movieDao().locationMetaCount(living.id!!))
        assertEquals(0, database.movieDao().locationMetaCount(bedroom.id!!))
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): SettingsViewModel =
        SettingsViewModel(
            handle,
            SettingsRepository(preferences),
            CacheRepository(database, profiles, connection)
        ).also { viewModels += it }

    private fun type(viewModel: SettingsViewModel, text: String) {
        viewModel.syncPiconsPath.state.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun saved(name: String): Profile = Profile.getDefault().apply {
        this.name = name
        host = "10.0.0.1"
    }.also { profiles.save(it) }

    private suspend fun cache(profileId: Int) {
        database.movieDao().replaceLocations(
            profileId,
            listOf(MovieLocationStripEntity(profileId, 0, "/hdd/movie"))
        )
    }

    private fun goOffline() {
        connection.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )
        assertEquals(ConnectionStatus.Session.Offline, connection.status.value.session)
    }
}
