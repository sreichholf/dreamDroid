package net.reichholf.dreamdroid.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.ui.nav.StartScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {
    private val preferences = MemorySharedPreferences()
    private val repository = SettingsRepository(preferences)

    @Test
    fun emptyPreferencesReadAsDefaults() {
        assertEquals(AppSettings(), repository.current())
    }

    @Test
    fun readsStoredValuesUnderTheSharedKeys() {
        preferences.edit()
            .putBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, true)
            .putBoolean(DreamDroid.PREFS_KEY_VOLUME_CONTROL, true)
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "2")
            .putString(DreamDroid.PREFS_KEY_START_SCREEN, StartScreen.VALUE_EPG)
            .apply()

        val settings = repository.current()

        assertEquals(true, settings.instantZap)
        assertEquals(true, settings.volumeControl)
        assertEquals("2", settings.themeType)
        assertEquals(StartScreen.VALUE_EPG, settings.startScreen)
    }

    @Test
    fun unknownStartScreenReadsAsServices() {
        preferences.edit()
            .putString(DreamDroid.PREFS_KEY_START_SCREEN, StartScreen.VALUE_CURRENT)
            .apply()

        assertEquals(StartScreen.VALUE_SERVICES, repository.current().startScreen)
    }

    @Test
    fun updateWritesOnlyTheChangedKeys() {
        val (before, after) = repository.update { it.copy(nowPlayingStrip = false) }

        assertEquals(true, before.nowPlayingStrip)
        assertEquals(false, after.nowPlayingStrip)
        assertEquals(mapOf(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP to false), preferences.all)
    }

    @Test
    fun everySettingSurvivesAFreshRead() {
        val changed = AppSettings(
            integratedVideoPlayer = false,
            videoEnableGestures = false,
            videoHardwareAcceleration = "0",
            startScreen = StartScreen.VALUE_ZAP,
            volumeControl = true,
            instantZap = true,
            nowPlayingStrip = false,
            simpleVrm = false,
            mobileImdb = true,
            confirmAppClose = false,
            playButtonAsPlayPause = true,
            themeType = "0",
            dynamicThemeColors = true,
            enableAnimations = false,
            gridMaxCols = "3",
            multiEpgTextSize = "compact",
            picons = true,
            piconsOnline = true,
            useNameAsPiconFilename = true,
            syncPiconsPath = "/media/hdd/picon",
            enableDeveloper = true,
            fakePicon = true,
            xmlDebug = true,
            autoSwitchProfileWifiBased = true
        )

        repository.update { changed }

        assertEquals(changed, SettingsRepository(preferences).current())
    }

    @Test
    fun settingsFlowEmitsTheCurrentValuesThenEachChange() {
        val seen = mutableListOf<AppSettings>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.launch { repository.settings.collect { seen += it } }

        repository.update { it.copy(picons = true) }
        preferences.edit().putString(DreamDroid.PREFS_KEY_GRID_MAX_COLS, "2").apply()
        preferences.edit().putString("unrelated", "x").apply()
        scope.cancel()

        assertEquals(
            listOf(
                AppSettings(),
                AppSettings(picons = true),
                AppSettings(picons = true, gridMaxCols = "2")
            ),
            seen
        )
    }

    @Test
    fun restoreKeepsValueTypes() {
        repository.restore(
            mapOf(
                DreamDroid.PREFS_KEY_XML_DEBUG to true,
                DreamDroid.CURRENT_PROFILE to 4,
                "long" to 5L,
                "float" to 1.5f,
                DreamDroid.PREFS_KEY_THEME_TYPE to "0"
            )
        )

        assertEquals(
            mapOf(
                DreamDroid.PREFS_KEY_XML_DEBUG to true,
                DreamDroid.CURRENT_PROFILE to 4,
                "long" to 5L,
                "float" to 1.5f,
                DreamDroid.PREFS_KEY_THEME_TYPE to "0"
            ),
            repository.all()
        )
        assertEquals(true, repository.current().xmlDebug)
    }
}
