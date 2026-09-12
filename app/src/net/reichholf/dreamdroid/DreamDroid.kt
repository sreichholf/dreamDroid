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
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.LocationListRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TagListRequestHandler
import net.reichholf.dreamdroid.room.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Objects

/**
 * @author sre
 */
class DreamDroid : Application() {

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
        val dao = AppDatabase.profiles(appContext)
        if (dao.getProfiles().size == 0) {
            val dbh = DatabaseHelper.getInstance(appContext)
            if (dbh.getProfiles().size > 0) {
                for (p in dbh.getProfiles()) {
                    dbh.deleteProfile(p)
                    p.setId(dao.addProfile(p))
                }
                // Legacy SQLite is migrate-only; drop the file once Room has the profiles.
                appContext.deleteDatabase(DatabaseHelper.DATABASE_NAME)
            }
        }

        initChannels()
        sLocations = ArrayList()
        sTags = ArrayList()

        loadCurrentProfile(this)

        handleProfileSwitch(this)
    }

    private fun handleProfileSwitch(@NonNull context: Context) {
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED, false,
            )
        ) {
            val currentWifiName = getWifiName(context)
            val currentProfile = getCurrentProfile()

            Log.i(LOG_TAG, "currentWifiName = $currentWifiName")
            Log.i(LOG_TAG, "currentProfileSsid = ${currentProfile.getSsid()}")
            val dao = AppDatabase.profiles(getAppContext()!!)
            if (currentWifiName == null) {
                Log.i(LOG_TAG, "not connected to wifi, will search for default profile")
                // not connected to wifi, search for default profile
                if (currentProfile.isDefaultProfileOnNoWifi()) {
                    Log.i(LOG_TAG, "currentProfile is default for NO WIFI, so no action required")
                } else {
                    val noWifiDefault = dao.getProfiles().firstOrNull { it.isDefaultProfileOnNoWifi() }
                    if (noWifiDefault != null) {
                        Log.i(LOG_TAG, "found profile for default ")
                        setCurrentProfile(context, noWifiDefault.getId())
                    } else {
                        Log.w(LOG_TAG, "no default profile on no wifi found in all profiles.")
                    }
                }
            } else {
                Log.i(
                    LOG_TAG,
                    "connected to wifi $currentWifiName will search for profile with this wifi name configured",
                )
                // we are connected to a wifi
                // check if current active profile fits to the wifi name
                if (currentWifiName.equals(currentProfile.getSsid(), ignoreCase = true)) {
                    Log.i(LOG_TAG, "currentProfile has correct wifi name configured, so no action required")
                } else {
                    Log.i(
                        LOG_TAG,
                        "connected to wifi $currentWifiName will search for profile with this wifi name configured",
                    )
                    val wifiProfile = dao.getProfiles()
                        .firstOrNull { p ->
                            p.getSsid() != null && p.getSsid()!!.equals(currentWifiName, ignoreCase = true)
                        }
                    if (wifiProfile != null) {
                        Log.i(LOG_TAG, "found profile with configured ssid ")
                        setCurrentProfile(context, wifiProfile.getId())
                    } else {
                        Log.w(LOG_TAG, "no profile found with ssid configured for $wifiProfile")
                    }
                }
            }
        }
    }

    @Nullable
    private fun getWifiName(@NonNull context: Context): String? {
        val manager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (manager.isWifiEnabled) {
            val wifiInfo = manager.connectionInfo
            if (wifiInfo != null) {
                val state = WifiInfo.getDetailedStateOf(wifiInfo.supplicantState)
                if (state == NetworkInfo.DetailedState.CONNECTED ||
                    state == NetworkInfo.DetailedState.OBTAINING_IPADDR
                ) {
                    return wifiInfo.ssid.substring(1, wifiInfo.ssid.length - 1)
                }
            }
        }
        return null
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
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = getString(R.string.sync_picons)
        notificationManager.createNotificationChannel(channel)

        channel = NotificationChannel(
            "dreamdroid_epg_sync",
            getString(R.string.epg),
            NotificationManager.IMPORTANCE_LOW,
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

        @JvmField
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
        const val PREFS_KEY_SIMPLE_VRM: String = "simple_vrm"
        const val PREFS_KEY_ENABLE_DEVELOPER_SETTINGS: String = "enable_developer"
        const val PREFS_KEY_FAKE_PICON: String = "fake_picon"
        const val PREFS_KEY_XML_DEBUG: String = "xml_debug"
        const val PREFS_KEY_INTEGRATED_PLAYER: String = "integrated_video_player"
        const val PREFS_KEY_THEME_TYPE: String = "theme_type"
        const val PREFS_KEY_INSTANT_ZAP: String = "instant_zap"
        const val PREFS_KEY_START_SCREEN: String = "start_screen"
        const val PREFS_KEY_VIDEO_ENABLE_GESTURES: String = "video_enable_gestures"
        const val PREFS_KEY_LAST_VERSION_CODE: String = "last_version_code"
        const val PREFS_KEY_AUTO_SWITCH_PROFILE_WIFI_BASED: String = "auto_switch_profile_wifi_based"
        const val PREFS_KEY_DYNAMIC_THEME_COLORS: String = "dynamic_theme_colors"

        const val IAB_PUB_KEY: String =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkWyCpE79iRAcqWnC+/I5AuahW/wvbGF5SxcZCELP6I6Rs47hYOydmCBDV5e11FXHZyS3BGuuVKEjf9DxkR2skNtKfgbX/UQD0jpnaEk2GnnsZ9OAaso9pKFn1ZJKtLtP7OKVlt2HpHjag3x8NGayjkno0k0gmvf5T8c77tYLtoHY+uLlUTwo0DiXhzxHjTjzTxc0nbEyRDa/5pDPudBCSien4lg+C8D9K8rdcUCI1QcLjkOgBR888CxT7cyhvUnoHcHZQLGbTFZG0XtyJnxop2AqWMiOepT3txAfq6OjOmo0PofuIk+m0jVrPLYs2eNSxmJrfZ5MddocPYD50cj+2QIDAQAB"

        const val SKU_DONATE_1: String = "donate_1"
        const val SKU_DONATE_2: String = "donate_2"
        const val SKU_DONATE_3: String = "donate_3"
        const val SKU_DONATE_5: String = "donate_5"
        const val SKU_DONATE_10: String = "donate_10"
        const val SKU_DONATE_15: String = "donate_15"
        const val SKU_DONATE_20: String = "donate_20"
        const val SKU_DONATE_INSANE: String = "donate_insane"

        @JvmField
        val SKU_LIST: Array<String> = arrayOf(
            SKU_DONATE_1,
            SKU_DONATE_2,
            SKU_DONATE_3,
            SKU_DONATE_5,
            SKU_DONATE_10,
            SKU_DONATE_15,
            SKU_DONATE_20,
            SKU_DONATE_INSANE,
        )

        const val CURRENT_PROFILE: String = "currentProfile"

        @JvmField
        var DATE_LOCALE_WO: Boolean = false

        private var sFeatureSleeptimer: Boolean = true
        private var sFeatureNowNext: Boolean = true
        private var sDumpXml: Boolean = false

        private var sProfile: Profile? = null
        private var sLocations: ArrayList<String> = ArrayList()
        private var sTags: ArrayList<String> = ArrayList()

        @Nullable
        private var sCurrentProfileChangedListener: ProfileChangedListener? = null

        private var sFeaturePostRequest: Boolean = true

        @JvmStatic
        @Nullable
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

        @JvmStatic
        @NonNull
        fun getVersionString(): String {
            var buildDate = "<build-no-date>"
            if (BuildConfig.BUILD_TIME > 0) {
                buildDate = DateTime.getYearDateTimeString(BuildConfig.BUILD_TIME / 1000)
            }
            var abi = Build.CPU_ABI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                abi = Build.SUPPORTED_ABIS[0]
            }
            return String.format(
                "dreamDroid %s\n%s-%s %s\n%s\n\n© Stephan Reichholf\nstephan@reichholf.net",
                BuildConfig.VERSION_NAME,
                BuildConfig.FLAVOR,
                BuildConfig.BUILD_TYPE,
                abi,
                buildDate,
            )
        }

        @JvmStatic
        fun disableNowNext() {
            sFeatureNowNext = false
        }

        @JvmStatic
        fun enableNowNext() {
            sFeatureNowNext = true
        }

        @JvmStatic
        fun featureNowNext(): Boolean {
            return sFeatureNowNext
        }

        @JvmStatic
        fun featurePostRequest(): Boolean {
            return sFeaturePostRequest
        }

        @JvmStatic
        fun setFeaturePostRequest(enabled: Boolean) {
            sFeaturePostRequest = enabled
        }

        @JvmStatic
        fun disableSleepTimer() {
            sFeatureSleeptimer = false
        }

        @JvmStatic
        fun enableSleepTimer() {
            sFeatureSleeptimer = true
        }

        @JvmStatic
        fun featureSleepTimer(): Boolean {
            return sFeatureSleeptimer
        }

        @JvmStatic
        fun getCurrentProfile(): Profile {
            return sProfile!!
        }

        @JvmStatic
        fun loadCurrentProfile(context: Context) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val profileId = sp.getInt(CURRENT_PROFILE, 1)
            if (sProfile != null && sProfile!!.getId() == profileId) {
                return
            }

            val dao = AppDatabase.profiles(context)
            val profiles = dao.getProfiles()
            // the profile-table is initial - let's migrate the current config as
            // default Profile
            if (profiles.isEmpty()) {
                val host = sp.getString("host", "dreamdroid.org")
                val streamHost = sp.getString("host", "")

                val port = Integer.valueOf(sp.getString("port", "443"))
                val user = sp.getString("user", "root")
                val pass = sp.getString("pass", "dreambox")

                val login = sp.getBoolean("login", false)
                val ssl = sp.getBoolean("ssl", true)

                val p = Profile(
                    null, "Demo", host, streamHost, port, 8001, 80, login, user, pass, ssl, false, false,
                    false, false, "", "", "", "",
                )
                p.setId(dao.addProfile(p))

                val editor = sp.edit()
                editor.remove(CURRENT_PROFILE)
                editor.apply()
            }

            if (!setCurrentProfile(context, profileId)) {
                // However we got here... we're creating an
                // "do-not-crash-default-profile now
                sProfile = Profile(
                    null, "Demo", "dreamdroid.org", "", 80, 8001, 80, false, "", "", false, false, false, false,
                    false, "", "", "", "",
                )
            }
        }

        @JvmStatic
        fun setCurrentProfile(context: Context, id: Int): Boolean {
            return setCurrentProfile(context, id, false)
        }

        @JvmStatic
        fun dumpXml(): Boolean {
            return sDumpXml
        }

        /**
         * @param id
         * @return
         */
        @JvmStatic
        fun setCurrentProfile(context: Context, id: Int, forceEvent: Boolean): Boolean {
            sDumpXml = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("xml_debug", false)

            var oldProfile = sProfile
            if (oldProfile == null) {
                oldProfile = Profile.getDefault()
            }

            @Suppress("SENSELESS_COMPARISON")
            sProfile = AppDatabase.profiles(context).getProfile(id)

            if (sProfile != null) {
                val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
                editor.putInt(CURRENT_PROFILE, id)
                editor.apply()
                if (!sProfile!!.equals(oldProfile) || forceEvent) {
                    // reset locations and tags, they will be reloaded when needed the next time
                    sLocations.clear()
                    sTags.clear()
                    activeProfileChanged()
                } else if (Objects.equals(sProfile!!.getId(), oldProfile.getId())) {
                    sProfile!!.setSessionId(oldProfile.getSessionId())
                }
                return true
            } else {
                Log.w(LOG_TAG, "no profile with given id [$id] found")
            }
            return false
        }

        @JvmStatic
        fun setCurrentProfile(profile: Profile) {
            sProfile = profile
        }

        @JvmStatic
        fun profileChanged(context: Context, @NonNull p: Profile) {
            if (Objects.equals(p.getId(), sProfile!!.getId())) {
                reloadCurrentProfile(context)
            }
        }

        private fun activeProfileChanged() {
            if (sCurrentProfileChangedListener != null) {
                sCurrentProfileChangedListener!!.onProfileChanged(sProfile!!)
            }
        }

        @JvmStatic
        fun setCurrentProfileChangedListener(listener: ProfileChangedListener?) {
            sCurrentProfileChangedListener = listener
        }

        /**
         * @return
         */
        @JvmStatic
        fun reloadCurrentProfile(ctx: Context): Boolean {
            return setCurrentProfile(ctx, sProfile!!.getId(), true)
        }

        /**
         * @param shc
         */
        @JvmStatic
        @Synchronized
        fun loadLocations(@NonNull shc: SimpleHttpClient): Boolean {
            sLocations.clear()

            var gotLoc = false
            val handler = LocationListRequestHandler()
            val xml = handler.getList(shc)

            if (xml != null) {
                if (handler.parseList(xml, sLocations)) {
                    gotLoc = true
                }
            }

            if (!gotLoc) {
                Log.e(LOG_TAG, "Error parsing locations, falling back to /hdd/movie")
                sLocations = ArrayList()
                sLocations.add("/hdd/movie")
            }

            return gotLoc
        }

        @JvmStatic
        fun getLocations(): ArrayList<String> {
            return sLocations
        }

        /**
         * @param shc
         */
        @JvmStatic
        @Synchronized
        fun loadTags(@NonNull shc: SimpleHttpClient): Boolean {
            sTags.clear()
            var gotTags = false

            val handler = TagListRequestHandler()

            val xmlLoc = handler.getList(shc)

            if (xmlLoc != null) {
                if (handler.parseList(xmlLoc, sTags)) {
                    gotTags = true
                }
            }

            if (!gotTags) {
                Log.e(LOG_TAG, "Error parsing Tags, no more Tags will be available")
                sTags = ArrayList()
            }

            return gotTags
        }

        @JvmStatic
        fun getTags(): ArrayList<String> {
            return sTags
        }

        @JvmStatic
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

        @JvmStatic
        fun getThemeType(context: Context): Int {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val type = Integer.parseInt(sp.getString("theme_type", "1"))
            return if (type > 2) 2 else type
        }

        @JvmStatic
        fun setTheme(@NonNull activity: AppCompatActivity) {
            val mode = when (getThemeType(activity)) {
                0 -> AppCompatDelegate.MODE_NIGHT_NO
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                2 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            activity.delegate.localNightMode = mode
        }

        @JvmStatic
        fun restart(context: Context) {
            val packageManager: PackageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName: ComponentName? = intent!!.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }

        @JvmStatic
        fun checkInitial(context: Context, which: Int): Boolean {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val mask = sp.getInt(PREFS_KEY_INITIALBITS, 0)

            return (mask and which) != which
        }

        @JvmStatic
        fun setNotInitial(context: Context, which: Int) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            var mask = sp.getInt(PREFS_KEY_INITIALBITS, 0)
            mask = mask or which

            val editor = sp.edit()
            editor.putInt(PREFS_KEY_INITIALBITS, mask)
            editor.apply()
        }

        @JvmStatic
        fun isTV(@NonNull context: Context): Boolean {
            return context.resources.getBoolean(R.bool.is_television)
        }
    }
}
