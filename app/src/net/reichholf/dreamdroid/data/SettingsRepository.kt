package net.reichholf.dreamdroid.data

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.nav.StartScreen
import net.reichholf.dreamdroid.video.VLCPlayer

/**
 * The settings screen's preferences. Defaults match `R.xml.preferences`. List values stay
 * the stored strings, because the screen matches them against its string-array values.
 */
data class AppSettings(
    val integratedVideoPlayer: Boolean = true,
    val videoEnableGestures: Boolean = true,
    val videoHardwareAcceleration: String = VLCPlayer.MEDIA_HWACCEL_ENABLED.toString(),
    val startScreen: String = StartScreen.VALUE_SERVICES,
    val volumeControl: Boolean = false,
    val instantZap: Boolean = false,
    val nowPlayingStrip: Boolean = true,
    val simpleVrm: Boolean = true,
    val mobileImdb: Boolean = false,
    val confirmAppClose: Boolean = true,
    val playButtonAsPlayPause: Boolean = false,
    val themeType: String = "1",
    val dynamicThemeColors: Boolean = false,
    val enableAnimations: Boolean = true,
    val gridMaxCols: String = "-1",
    val multiEpgTextSize: String = MultiEpgTextSize.DEFAULT.prefValue,
    val picons: Boolean = false,
    val piconsOnline: Boolean = false,
    val useNameAsPiconFilename: Boolean = false,
    val syncPiconsPath: String = "/usr/share/enigma2/picon",
    val enableDeveloper: Boolean = false,
    val fakePicon: Boolean = false,
    val xmlDebug: Boolean = false,
    val autoSwitchProfileWifiBased: Boolean = false
)

/**
 * [AppSettings] over the default [SharedPreferences], under the `DreamDroid.PREFS_KEY_*`
 * keys that the rest of the app and backups read. A later DataStore swap (B2) changes
 * only this class.
 */
@Singleton
class SettingsRepository @Inject constructor(private val preferences: SharedPreferences) {
    /** The current settings, then again after every preference write. */
    val settings: Flow<AppSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        send(current())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    fun current(): AppSettings = AppSettings(
        integratedVideoPlayer = bool(
            DreamDroid.PREFS_KEY_INTEGRATED_PLAYER,
            DEFAULTS.integratedVideoPlayer
        ),
        videoEnableGestures = bool(
            DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES,
            DEFAULTS.videoEnableGestures
        ),
        videoHardwareAcceleration = string(
            DreamDroid.PREFS_KEY_HWACCEL,
            DEFAULTS.videoHardwareAcceleration
        ),
        startScreen = StartScreen.read(preferences),
        volumeControl = bool(DreamDroid.PREFS_KEY_VOLUME_CONTROL, DEFAULTS.volumeControl),
        instantZap = bool(DreamDroid.PREFS_KEY_INSTANT_ZAP, DEFAULTS.instantZap),
        nowPlayingStrip = bool(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, DEFAULTS.nowPlayingStrip),
        simpleVrm = bool(DreamDroid.PREFS_KEY_SIMPLE_VRM, DEFAULTS.simpleVrm),
        mobileImdb = bool(DreamDroid.PREFS_KEY_MOBILE_IMDB, DEFAULTS.mobileImdb),
        confirmAppClose = bool(DreamDroid.PREFS_KEY_CONFIRM_APP_CLOSE, DEFAULTS.confirmAppClose),
        playButtonAsPlayPause = bool(
            DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE,
            DEFAULTS.playButtonAsPlayPause
        ),
        themeType = string(DreamDroid.PREFS_KEY_THEME_TYPE, DEFAULTS.themeType),
        dynamicThemeColors = bool(
            DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS,
            DEFAULTS.dynamicThemeColors
        ),
        enableAnimations = bool(
            DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS,
            DEFAULTS.enableAnimations
        ),
        gridMaxCols = string(DreamDroid.PREFS_KEY_GRID_MAX_COLS, DEFAULTS.gridMaxCols),
        multiEpgTextSize = string(
            DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE,
            DEFAULTS.multiEpgTextSize
        ),
        picons = bool(DreamDroid.PREFS_KEY_PICONS_ENABLED, DEFAULTS.picons),
        piconsOnline = bool(DreamDroid.PREFS_KEY_PICONS_ONLINE, DEFAULTS.piconsOnline),
        useNameAsPiconFilename = bool(
            DreamDroid.PREFS_KEY_PICONS_USE_NAME,
            DEFAULTS.useNameAsPiconFilename
        ),
        syncPiconsPath = string(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, DEFAULTS.syncPiconsPath),
        enableDeveloper = bool(
            DreamDroid.PREFS_KEY_ENABLE_DEVELOPER_SETTINGS,
            DEFAULTS.enableDeveloper
        ),
        fakePicon = bool(DreamDroid.PREFS_KEY_FAKE_PICON, DEFAULTS.fakePicon),
        xmlDebug = bool(DreamDroid.PREFS_KEY_XML_DEBUG, DEFAULTS.xmlDebug),
        autoSwitchProfileWifiBased = bool(
            DreamDroid.PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED,
            DEFAULTS.autoSwitchProfileWifiBased
        )
    )

    /**
     * Applies [transform] to the current settings and writes the keys whose value changed.
     * Returns the settings before and after.
     */
    fun update(transform: (AppSettings) -> AppSettings): Pair<AppSettings, AppSettings> {
        val before = current()
        val after = transform(before)
        if (after != before) {
            val editor = preferences.edit()
            BOOLEANS.forEach { (key, value) ->
                if (value(after) != value(before)) editor.putBoolean(key, value(after))
            }
            STRINGS.forEach { (key, value) ->
                if (value(after) != value(before)) editor.putString(key, value(after))
            }
            editor.apply()
        }
        return before to after
    }

    /**
     * Whether the drawer's EPG entry opens MultiEPG instead of the list EPG. The EPG jumps
     * of the bouquet screens set it; see [DrawerEpgMode].
     */
    var drawerEpgMulti: Boolean
        get() = DrawerEpgMode.isMulti(preferences)
        set(value) = DrawerEpgMode.save(
            preferences,
            if (value) DrawerEpgMode.MULTI else DrawerEpgMode.LIST
        )

    /** Every stored preference, for a backup. */
    fun all(): Map<String, Any?> = HashMap(preferences.all)

    /**
     * Stores [values] from a backup. Booleans, ints, longs, and floats keep their type;
     * anything else is stored as its string.
     */
    fun restore(values: Map<String, Any?>) {
        val editor = preferences.edit()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                else -> editor.putString(key, value?.toString())
            }
        }
        editor.apply()
    }

    private fun bool(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)

    private fun string(key: String, default: String): String =
        preferences.getString(key, default) ?: default

    private companion object {
        val DEFAULTS = AppSettings()

        val BOOLEANS: List<Pair<String, (AppSettings) -> Boolean>> = listOf(
            DreamDroid.PREFS_KEY_INTEGRATED_PLAYER to AppSettings::integratedVideoPlayer,
            DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES to AppSettings::videoEnableGestures,
            DreamDroid.PREFS_KEY_VOLUME_CONTROL to AppSettings::volumeControl,
            DreamDroid.PREFS_KEY_INSTANT_ZAP to AppSettings::instantZap,
            DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP to AppSettings::nowPlayingStrip,
            DreamDroid.PREFS_KEY_SIMPLE_VRM to AppSettings::simpleVrm,
            DreamDroid.PREFS_KEY_MOBILE_IMDB to AppSettings::mobileImdb,
            DreamDroid.PREFS_KEY_CONFIRM_APP_CLOSE to AppSettings::confirmAppClose,
            DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE to AppSettings::playButtonAsPlayPause,
            DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS to AppSettings::dynamicThemeColors,
            DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS to AppSettings::enableAnimations,
            DreamDroid.PREFS_KEY_PICONS_ENABLED to AppSettings::picons,
            DreamDroid.PREFS_KEY_PICONS_ONLINE to AppSettings::piconsOnline,
            DreamDroid.PREFS_KEY_PICONS_USE_NAME to AppSettings::useNameAsPiconFilename,
            DreamDroid.PREFS_KEY_ENABLE_DEVELOPER_SETTINGS to AppSettings::enableDeveloper,
            DreamDroid.PREFS_KEY_FAKE_PICON to AppSettings::fakePicon,
            DreamDroid.PREFS_KEY_XML_DEBUG to AppSettings::xmlDebug,
            DreamDroid.PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED to
                AppSettings::autoSwitchProfileWifiBased
        )

        val STRINGS: List<Pair<String, (AppSettings) -> String>> = listOf(
            DreamDroid.PREFS_KEY_HWACCEL to AppSettings::videoHardwareAcceleration,
            DreamDroid.PREFS_KEY_START_SCREEN to AppSettings::startScreen,
            DreamDroid.PREFS_KEY_THEME_TYPE to AppSettings::themeType,
            DreamDroid.PREFS_KEY_GRID_MAX_COLS to AppSettings::gridMaxCols,
            DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE to AppSettings::multiEpgTextSize,
            DreamDroid.PREFS_KEY_SYNC_PICONS_PATH to AppSettings::syncPiconsPath
        )
    }
}
