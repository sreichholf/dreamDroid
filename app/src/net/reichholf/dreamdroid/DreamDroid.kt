/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.WifiSsid
import net.reichholf.dreamdroid.helpers.enigma2.PiconImageLoader
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * @author sre
 */
class DreamDroid : Application() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /*
     * (non-Javadoc)
     *
     * @see android.app.Application#onCreate()
     */
    override fun onCreate() {
        super.onCreate()
        val dynamicColors = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(PREFS_KEY_DYNAMIC_THEME_COLORS, false)
        if (dynamicColors) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        // Determine if we require a Date-String-Locale-Missing-Fix
        // for details please see:
        // http://code.google.com/p/android/issues/detail?id=9453
        val sdf = SimpleDateFormat("E")
        val date: Date = GregorianCalendar.getInstance().time
        VERSION_STRING = getVersionString()

        try {
            val s = sdf.format(date)
            //noinspection ResultOfMethodCallIgnored
            Integer.parseInt(s)
            DATE_LOCALE_WO = true
        } catch (e: Exception) {
            DATE_LOCALE_WO = false
        }

        val appContext = getAppContext()!!
        DatabaseHelper.migrateIntoRoomIfNeeded(appContext)

        initChannels()
        ProfileRepository.install(this)
        ProfileRepository.get().loadCurrent(this)

        handleProfileSwitch(this)
        PiconImageLoader.install(this)
        pruneExpiredMultiEpgCache()
    }

    private fun pruneExpiredMultiEpgCache() {
        ioScope.launch {
            try {
                val nowSec = System.currentTimeMillis() / 1000L
                AppDatabase.epg(this@DreamDroid).pruneOlderThan(
                    MultiEpgWindows.retentionCutoffSec(nowSec)
                )
            } catch (t: Throwable) {
                Log.w(LOG_TAG, "MultiEPG cache prune failed", t)
            }
        }
    }

    private fun handleProfileSwitch(context: Context) {
        val profiles = ProfileRepository.get()
        val currentProfile = profiles.current.value ?: return
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED,
                false
            )
        ) {
            val currentWifiName = getWifiName(context)

            Log.i(LOG_TAG, "currentWifiName = $currentWifiName")
            Log.i(LOG_TAG, "currentProfileSsid = ${currentProfile.ssid}")
            val rows = profilesStore(context)
            if (currentWifiName == null) {
                Log.i(LOG_TAG, "not connected to wifi, will search for default profile")
                // not connected to wifi, search for default profile
                if (currentProfile.isDefaultProfileOnNoWifi) {
                    Log.i(LOG_TAG, "currentProfile is default for NO WIFI, so no action required")
                } else {
                    val noWifiDefault = rows.firstOrNull { it.isDefaultProfileOnNoWifi }
                    if (noWifiDefault != null) {
                        Log.i(LOG_TAG, "found profile for default ")
                        profiles.setCurrent(context, noWifiDefault.id ?: -1)
                    } else {
                        Log.w(LOG_TAG, "no default profile on no wifi found in all profiles.")
                    }
                }
            } else {
                Log.i(
                    LOG_TAG,
                    "connected to wifi $currentWifiName will search for profile " +
                        "with this wifi name configured"
                )
                // we are connected to a wifi
                // check if current active profile fits to the wifi name
                if (currentWifiName.equals(currentProfile.ssid, ignoreCase = true)) {
                    Log.i(
                        LOG_TAG,
                        "currentProfile has correct wifi name configured, so no action required"
                    )
                } else {
                    Log.i(
                        LOG_TAG,
                        "connected to wifi $currentWifiName will search for profile " +
                            "with this wifi name configured"
                    )
                    val wifiProfile = rows.firstOrNull { p ->
                        p.ssid != null && p.ssid.equals(currentWifiName, ignoreCase = true)
                    }
                    if (wifiProfile != null) {
                        Log.i(LOG_TAG, "found profile with configured ssid ")
                        profiles.setCurrent(context, wifiProfile.id ?: -1)
                    } else {
                        Log.w(LOG_TAG, "no profile found with ssid configured for $wifiProfile")
                    }
                }
            }
        }
    }

    private fun profilesStore(context: Context) =
        AppDatabase.profilesBlocking(context).getProfiles()

    private fun getWifiName(context: Context): String? {
        val appContext = context.applicationContext
        val connectivity =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }
        val wifiInfo: WifiInfo? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val manager =
                    appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                manager.connectionInfo
            } else {
                caps.transportInfo as? WifiInfo
            }
        return WifiSsid.unquoteWifiSsid(wifiInfo?.ssid)
    }

    private fun initChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var channel = NotificationChannel(
            "dreamdroid_picon_sync",
            getString(R.string.picons),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.sync_picons)
        notificationManager.createNotificationChannel(channel)

        channel = NotificationChannel(
            "dreamdroid_epg_sync",
            getString(R.string.epg),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.epg_sync)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        @Volatile
        private var instance: DreamDroid? = null

        const val INITIAL_SERVICELIST_PANE: Int = 1
        const val INITIAL_VIRTUAL_REMOTE: Int = 2
        const val PREFS_KEY_HWACCEL: String = "video_hardware_acceleration"
        const val PREFS_KEY_PICONS_ONLINE: String = "picons_online"

        var VERSION_STRING: String = ""

        const val ACTION_CREATE: String = "dreamdroid.intent.action.NEW"
        const val LOG_TAG: String = "DreamDroid"

        const val PREFS_KEY_QUICKZAP: String = "quickzap"
        const val PREFS_KEY_CONFIRM_APP_CLOSE: String = "confirm_app_close"
        const val PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE: String = "play_button_as_play_pause"
        const val PREFS_KEY_ENABLE_ANIMATIONS: String = "enable_animations"
        const val PREFS_KEY_FIRST_START: String = "first_start"
        const val PREFS_KEY_SYNC_PICONS: String = "sync_picons"
        const val PREFS_KEY_SYNC_PICONS_PATH: String = "sync_picons_path"
        const val PREFS_KEY_PICONS_ENABLED: String = "picons"
        const val PREFS_KEY_PICONS_USE_NAME: String = "use_name_as_picon_filename"
        const val PREFS_KEY_INITIALBITS: String = "initial_bits"
        const val PREFS_KEY_GRID_MAX_COLS: String = "grid_max_cols"
        const val PREFS_KEY_MULTIEPG_TEXT_SIZE: String = "multiepg_text_size"
        const val PREFS_KEY_DRAWER_EPG_MODE: String = "drawer_epg_mode"
        const val PREFS_KEY_SIMPLE_VRM: String = "simple_vrm"
        const val PREFS_KEY_ENABLE_DEVELOPER_SETTINGS: String = "enable_developer"
        const val PREFS_KEY_FAKE_PICON: String = "fake_picon"
        const val PREFS_KEY_XML_DEBUG: String = "xml_debug"
        const val PREFS_KEY_INTEGRATED_PLAYER: String = "integrated_video_player"
        const val PREFS_KEY_THEME_TYPE: String = "theme_type"
        const val PREFS_KEY_INSTANT_ZAP: String = "instant_zap"
        const val PREFS_KEY_NOW_PLAYING_STRIP: String = "now_playing_strip"
        const val PREFS_KEY_START_SCREEN: String = "start_screen"
        const val PREFS_KEY_VIDEO_ENABLE_GESTURES: String = "video_enable_gestures"
        const val PREFS_KEY_LAST_VERSION_CODE: String = "last_version_code"
        const val PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED: String =
            "auto_switch_profile_wifi_based"
        const val PREFS_KEY_DYNAMIC_THEME_COLORS: String = "dynamic_theme_colors"

        const val IAB_PUB_KEY: String =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkWyCpE79iRAcqWnC+/I5AuahW/wv" +
                "bGF5SxcZCELP6I6Rs47hYOydmCBDV5e11FXHZyS3BGuuVKEjf9DxkR2skNtKfgbX/UQD0jpn" +
                "aEk2GnnsZ9OAaso9pKFn1ZJKtLtP7OKVlt2HpHjag3x8NGayjkno0k0gmvf5T8c77tYLtoHY" +
                "+uLlUTwo0DiXhzxHjTjzTxc0nbEyRDa/5pDPudBCSien4lg+C8D9K8rdcUCI1QcLjkOgBR88" +
                "8CxT7cyhvUnoHcHZQLGbTFZG0XtyJnxop2AqWMiOepT3txAfq6OjOmo0PofuIk+m0jVrPLYs" +
                "2eNSxmJrfZ5MddocPYD50cj+2QIDAQAB"

        const val SKU_DONATE_1: String = "donate_1"
        const val SKU_DONATE_2: String = "donate_2"
        const val SKU_DONATE_3: String = "donate_3"
        const val SKU_DONATE_5: String = "donate_5"
        const val SKU_DONATE_10: String = "donate_10"
        const val SKU_DONATE_15: String = "donate_15"
        const val SKU_DONATE_20: String = "donate_20"
        const val SKU_DONATE_INSANE: String = "donate_insane"

        val SKU_LIST: Array<String> = arrayOf(
            SKU_DONATE_1,
            SKU_DONATE_2,
            SKU_DONATE_3,
            SKU_DONATE_5,
            SKU_DONATE_10,
            SKU_DONATE_15,
            SKU_DONATE_20,
            SKU_DONATE_INSANE
        )

        const val CURRENT_PROFILE: String = "currentProfile"

        var DATE_LOCALE_WO: Boolean = false

        private var sleepTimerEnabled: Boolean = true
        private var nowNextEnabled: Boolean = true

        private var postRequestEnabled: Boolean = true

        fun getAppContext(): Context? {
            if (instance != null) {
                return instance
            } else {
                try {
                    @Suppress("UNCHECKED_CAST")
                    instance = Class.forName("android.app.ActivityThread")
                        .getDeclaredMethod("currentApplication")
                        .invoke(null) as DreamDroid?
                } catch (ignored: IllegalAccessException) {
                } catch (ignored: java.lang.reflect.InvocationTargetException) {
                } catch (ignored: NoSuchMethodException) {
                } catch (ignored: ClassNotFoundException) {
                } catch (ignored: ClassCastException) {
                }
                return instance
            }
        }

        fun getVersionString(): String {
            var buildDate = "<build-no-date>"
            if (BuildConfig.BUILD_TIME > 0) {
                buildDate = DateTime.getYearDateTimeString(BuildConfig.BUILD_TIME / 1000)
            }
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            return String.format(
                "dreamDroid %s\n%s-%s %s\n%s\n\n© Stephan Reichholf\nstephan@reichholf.net",
                BuildConfig.VERSION_NAME,
                BuildConfig.FLAVOR,
                BuildConfig.BUILD_TYPE,
                abi,
                buildDate
            )
        }

        fun disableNowNext() {
            nowNextEnabled = false
        }

        fun enableNowNext() {
            nowNextEnabled = true
        }

        fun featureNowNext(): Boolean = nowNextEnabled

        fun featurePostRequest(): Boolean = postRequestEnabled

        fun setFeaturePostRequest(enabled: Boolean) {
            postRequestEnabled = enabled
        }

        fun disableSleepTimer() {
            sleepTimerEnabled = false
        }

        fun enableSleepTimer() {
            sleepTimerEnabled = true
        }

        fun featureSleepTimer(): Boolean = sleepTimerEnabled

        fun dumpXml(): Boolean = ProfileRepository.get().dumpXml()

        @Suppress("rawtypes", "unchecked", "UNCHECKED_CAST")
        fun scheduleBackup(context: Context) {
            Log.d(LOG_TAG, "Scheduling backup")
            try {
                val managerClass = Class.forName("android.app.backup.BackupManager")
                val managerConstructor = managerClass.getConstructor(Context::class.java)
                val manager = managerConstructor.newInstance(context)
                val m = managerClass.getMethod("dataChanged")
                m.invoke(manager)
                Log.d(LOG_TAG, "Backup requested")
            } catch (e: ClassNotFoundException) {
                Log.d(LOG_TAG, "No backup manager found")
            } catch (t: Throwable) {
                Log.d(LOG_TAG, "Scheduling backup failed $t")
                t.printStackTrace()
            }
        }

        fun getThemeType(context: Context): Int {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val type = Integer.parseInt(sp.getString("theme_type", "1") ?: "1")
            return if (type > 2) 2 else type
        }

        fun setTheme(activity: AppCompatActivity) {
            val mode = when (getThemeType(activity)) {
                0 -> AppCompatDelegate.MODE_NIGHT_NO
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                2 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            activity.delegate.localNightMode = mode
        }

        fun restart(context: Context) {
            val packageManager: PackageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName: ComponentName? = intent!!.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }

        fun checkInitial(context: Context, which: Int): Boolean {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val mask = sp.getInt(PREFS_KEY_INITIALBITS, 0)

            return (mask and which) != which
        }

        fun setNotInitial(context: Context, which: Int) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            var mask = sp.getInt(PREFS_KEY_INITIALBITS, 0)
            mask = mask or which

            val editor = sp.edit()
            editor.putInt(PREFS_KEY_INITIALBITS, mask)
            editor.apply()
        }

        fun isTV(context: Context): Boolean = context.resources.getBoolean(R.bool.is_television)
    }
}
