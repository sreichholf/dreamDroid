package net.reichholf.dreamdroid.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.video.VLCPlayer

/**
 * Preference keys and defaults match [R.xml.preferences] / PreferenceManager.
 * Values are read and written through the default SharedPreferences.
 */
class SettingsState(
    private val prefs: SharedPreferences,
    val showDeveloperCategory: Boolean,
    val showDynamicThemeColors: Boolean,
) {
    var integratedVideoPlayer by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true),
    )
    var videoEnableGestures by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES, true),
    )
    var videoHardwareAcceleration by mutableStateOf(
        prefs.getString(
            DreamDroid.PREFS_KEY_HWACCEL,
            VLCPlayer.MEDIA_HWACCEL_ENABLED.toString(),
        ) ?: VLCPlayer.MEDIA_HWACCEL_ENABLED.toString(),
    )

    var volumeControl by mutableStateOf(prefs.getBoolean(KEY_VOLUME_CONTROL, false))
    var instantZap by mutableStateOf(prefs.getBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, false))
    var simpleVrm by mutableStateOf(prefs.getBoolean(DreamDroid.PREFS_KEY_SIMPLE_VRM, true))
    var mobileImdb by mutableStateOf(prefs.getBoolean(KEY_MOBILE_IMDB, false))
    var confirmAppClose by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_CONFIRM_APP_CLOSE, true),
    )
    var playButtonAsPlayPause by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false),
    )

    var themeType by mutableStateOf(
        prefs.getString(DreamDroid.PREFS_KEY_THEME_TYPE, "1") ?: "1",
    )
    var dynamicThemeColors by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS, false),
    )
    var enableAnimations by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true),
    )
    var gridMaxCols by mutableStateOf(
        prefs.getString(DreamDroid.PREFS_KEY_GRID_MAX_COLS, "-1") ?: "-1",
    )

    var picons by mutableStateOf(prefs.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false))
    var piconsOnline by mutableStateOf(prefs.getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, false))
    var useNameAsPiconFilename by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false),
    )
    var syncPiconsPath by mutableStateOf(
        prefs.getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, DEFAULT_SYNC_PICONS_PATH)
            ?: DEFAULT_SYNC_PICONS_PATH,
    )

    var enableDeveloper by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_ENABLE_DEVELOPER_SETTINGS, false),
    )
    var fakePicon by mutableStateOf(prefs.getBoolean(DreamDroid.PREFS_KEY_FAKE_PICON, false))
    var xmlDebug by mutableStateOf(prefs.getBoolean(DreamDroid.PREFS_KEY_XML_DEBUG, false))

    var autoSwitchProfileWifiBased by mutableStateOf(
        prefs.getBoolean(DreamDroid.PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED, false),
    )

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        when (key) {
            DreamDroid.PREFS_KEY_INTEGRATED_PLAYER -> integratedVideoPlayer = value
            DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES -> videoEnableGestures = value
            KEY_VOLUME_CONTROL -> volumeControl = value
            DreamDroid.PREFS_KEY_INSTANT_ZAP -> instantZap = value
            DreamDroid.PREFS_KEY_SIMPLE_VRM -> simpleVrm = value
            KEY_MOBILE_IMDB -> mobileImdb = value
            DreamDroid.PREFS_KEY_CONFIRM_APP_CLOSE -> confirmAppClose = value
            DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE -> playButtonAsPlayPause = value
            DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS -> dynamicThemeColors = value
            DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS -> enableAnimations = value
            DreamDroid.PREFS_KEY_PICONS_ENABLED -> picons = value
            DreamDroid.PREFS_KEY_PICONS_ONLINE -> piconsOnline = value
            DreamDroid.PREFS_KEY_PICONS_USE_NAME -> useNameAsPiconFilename = value
            DreamDroid.PREFS_KEY_ENABLE_DEVELOPER_SETTINGS -> enableDeveloper = value
            DreamDroid.PREFS_KEY_FAKE_PICON -> fakePicon = value
            DreamDroid.PREFS_KEY_XML_DEBUG -> xmlDebug = value
            DreamDroid.PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED -> autoSwitchProfileWifiBased = value
        }
    }

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        when (key) {
            DreamDroid.PREFS_KEY_HWACCEL -> videoHardwareAcceleration = value
            DreamDroid.PREFS_KEY_THEME_TYPE -> themeType = value
            DreamDroid.PREFS_KEY_GRID_MAX_COLS -> gridMaxCols = value
            DreamDroid.PREFS_KEY_SYNC_PICONS_PATH -> syncPiconsPath = value
        }
    }

    companion object {
        const val KEY_VOLUME_CONTROL = "volume_control"
        const val KEY_MOBILE_IMDB = "mobile_imdb"
        const val DEFAULT_SYNC_PICONS_PATH = "/usr/share/enigma2/picon"

        @JvmStatic
        fun create(context: Context): SettingsState {
            PreferenceManager.setDefaultValues(context, R.xml.preferences, false)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val debuggable =
                0 != (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
            return SettingsState(
                prefs = prefs,
                showDeveloperCategory = debuggable,
                showDynamicThemeColors = DynamicColors.isDynamicColorAvailable(),
            )
        }
    }
}
