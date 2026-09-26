package net.reichholf.dreamdroid.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.LocationListRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TagListRequestHandler
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.setup.matchesSeededDemo
import net.reichholf.dreamdroid.ui.setup.soleSeededDemo

/**
 * Room profiles and the active profile. [current] is the source of truth.
 * [switches] emits once when the active profile actually changes (settings differ
 * or the caller forces the event). Location lists, tag lists, and device-info XML
 * live here and are cleared on that change.
 */
class ProfileRepository(private val store: ProfileStore) {
    private val _current = MutableStateFlow<Profile?>(null)
    val current: StateFlow<Profile?> = _current.asStateFlow()

    private val _switches = MutableSharedFlow<Profile>(extraBufferCapacity = 1)
    val switches: SharedFlow<Profile> = _switches.asSharedFlow()

    private var locationList: ArrayList<String> = ArrayList()
    private var tagList: ArrayList<String> = ArrayList()

    /**
     * True when [locationList] came from a successful locations HTTP parse.
     * False for empty, profile reset, or the `/hdd/movie` load-failure fallback.
     */
    @Volatile
    private var locationsFromReceiver: Boolean = false

    private val deviceInfo = HashMap<Int, String>()

    @Volatile
    private var xmlDump: Boolean = false

    fun requireCurrent(): Profile = current.value!!

    fun hasCurrent(): Boolean = current.value != null

    fun dumpXml(): Boolean = xmlDump

    fun locations(): ArrayList<String> = locationList

    fun tags(): ArrayList<String> = tagList

    fun locationsLoadedFromReceiver(): Boolean = locationsFromReceiver

    /** Device-info XML for a saved profile. Drafts with no id are never cached. */
    @Synchronized
    fun deviceInfo(profile: Profile): String? {
        val id = profile.id ?: return null
        return deviceInfo[id]
    }

    @Synchronized
    fun setDeviceInfo(profile: Profile, xml: String?) {
        val id = profile.id ?: return
        if (xml == null) {
            deviceInfo.remove(id)
        } else {
            deviceInfo[id] = xml
        }
    }

    /**
     * Marks whether the current location list was parsed from the receiver.
     * Profile activation clears this with the other per-profile caches.
     */
    internal fun setLocationsLoadedFromReceiver(loaded: Boolean) {
        locationsFromReceiver = loaded
    }

    fun setCurrent(profile: Profile) {
        _current.value = profile
    }

    fun setCurrent(context: Context, id: Int, forceEvent: Boolean = false): Boolean {
        xmlDump = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(DreamDroid.PREFS_KEY_XML_DEBUG, false)
        val activated = activate(id, forceEvent)
        if (activated) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(DreamDroid.CURRENT_PROFILE, id)
                .apply()
        }
        return activated
    }

    /**
     * Loads [id] from [store], publishes it on [current], and emits [switches] once
     * when the row differs from the active profile or [forceEvent] is true.
     * That path clears locations, tags, and device-info XML.
     */
    @Synchronized
    internal fun activate(id: Int, forceEvent: Boolean): Boolean {
        val oldProfile = _current.value ?: Profile.getDefault()
        val loaded = store.profile(id)
        if (loaded == null) {
            Log.w(DreamDroid.LOG_TAG, "no profile with given id [$id] found")
            return false
        }
        val changed = !loaded.hasSameSettings(oldProfile) || forceEvent
        if (changed) {
            clearPerProfileCaches()
            _current.value = loaded
            _switches.tryEmit(loaded)
        } else {
            if (loaded.id == oldProfile.id && _current.value != null) {
                loaded.sessionId = oldProfile.sessionId
            }
            _current.value = loaded
        }
        return true
    }

    fun clearCurrent() {
        _current.value = null
    }

    fun ensureCurrent(context: Context): Boolean {
        soleSeededDemo(store.profiles())?.let { store.delete(it) }
        val profiles = store.profiles()
        if (profiles.isEmpty()) {
            clearCurrent()
            return false
        }
        val currentId = current.value?.id
        if (currentId != null && profiles.any { it.id == currentId }) {
            return true
        }
        val first = profiles.first().id ?: return false
        return setCurrent(context, first, forceEvent = true)
    }

    fun loadCurrent(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val profileId = sp.getInt(DreamDroid.CURRENT_PROFILE, -1)
        val active = current.value
        if (active != null && profileId > 0 && active.id == profileId) {
            return
        }
        soleSeededDemo(store.profiles())?.let { store.delete(it) }
        if (store.profiles().isEmpty()) {
            val candidate = legacyPreferenceProfile(sp)
            if (!candidate.matchesSeededDemo()) {
                val newId = store.add(candidate).toInt()
                setCurrent(context, newId, forceEvent = true)
                return
            }
        }
        if (profileId > 0 && setCurrent(context, profileId)) {
            return
        }
        val first = store.profiles().firstOrNull()?.id
        if (first != null && setCurrent(context, first)) {
            return
        }
        clearCurrent()
    }

    fun reloadCurrent(context: Context): Boolean {
        val id = current.value?.id ?: return false
        return setCurrent(context, id, forceEvent = true)
    }

    @Synchronized
    fun loadLocations(http: EnigmaHttp): Boolean {
        locationList.clear()
        locationsFromReceiver = false
        var gotLoc = false
        val handler = LocationListRequestHandler()
        val xml = handler.getList(http)
        if (xml != null) {
            if (handler.parseList(xml, locationList)) {
                gotLoc = true
            }
        }
        if (!gotLoc) {
            Log.e(DreamDroid.LOG_TAG, "Error parsing locations, falling back to /hdd/movie")
            locationList = ArrayList()
            locationList.add("/hdd/movie")
        } else {
            locationsFromReceiver = true
        }
        return gotLoc
    }

    @Synchronized
    fun loadTags(http: EnigmaHttp): Boolean {
        tagList.clear()
        var gotTags = false
        val handler = TagListRequestHandler()
        val xml = handler.getList(http)
        if (xml != null) {
            if (handler.parseList(xml, tagList)) {
                gotTags = true
            }
        }
        if (!gotTags) {
            Log.e(DreamDroid.LOG_TAG, "Error parsing Tags, no more Tags will be available")
            tagList = ArrayList()
        }
        return gotTags
    }

    @Synchronized
    private fun clearPerProfileCaches() {
        locationList.clear()
        locationsFromReceiver = false
        tagList.clear()
        deviceInfo.clear()
    }

    private fun legacyPreferenceProfile(sp: SharedPreferences): Profile {
        val host = sp.getString("host", "dreamdroid.org")
        val streamHost = sp.getString("host", "")
        val port = Integer.valueOf(sp.getString("port", "443") ?: "443")
        val user = sp.getString("user", "root")
        val pass = sp.getString("pass", "dreambox")
        val login = sp.getBoolean("login", false)
        val ssl = sp.getBoolean("ssl", true)
        return Profile(
            null,
            "Demo",
            host,
            streamHost,
            port,
            8001,
            80,
            login,
            user,
            pass,
            ssl,
            false,
            false,
            false,
            false,
            "",
            "",
            "",
            ""
        )
    }

    companion object {
        @Volatile
        private var instance: ProfileRepository? = null

        fun install(context: Context): ProfileRepository {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: ProfileRepository(
                    RoomProfileStore(context.applicationContext)
                ).also { instance = it }
            }
        }

        fun get(): ProfileRepository {
            instance?.let { return it }
            val context = DreamDroid.getAppContext()
                ?: error("ProfileRepository used before Application")
            return install(context)
        }
    }
}

/** Profile rows the repository reads and writes. Room in the app, memory in JVM tests. */
interface ProfileStore {
    fun profiles(): List<Profile>

    fun profile(id: Int): Profile?

    fun add(profile: Profile): Long

    fun delete(profile: Profile)
}

class RoomProfileStore(private val context: Context) : ProfileStore {
    override fun profiles(): List<Profile> = AppDatabase.profilesBlocking(context).getProfiles()

    override fun profile(id: Int): Profile? = AppDatabase.profilesBlocking(context).getProfile(id)

    override fun add(profile: Profile): Long =
        AppDatabase.profilesBlocking(context).addProfile(profile)

    override fun delete(profile: Profile) {
        AppDatabase.profilesBlocking(context).deleteProfile(profile)
    }
}
