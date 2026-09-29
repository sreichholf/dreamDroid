package net.reichholf.dreamdroid.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.StringListParser
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.ProfileDaoBlocking
import net.reichholf.dreamdroid.ui.setup.matchesSeededDemo
import net.reichholf.dreamdroid.ui.setup.soleSeededDemo

/**
 * Room profiles and the active profile. [current] is the source of truth.
 * [switches] emits once when the active profile actually changes (settings differ
 * or the caller forces the event). Location lists, tag lists, and device-info XML
 * live here and are cleared on that change.
 *
 * The constructor must not read [store]: Hilt builds this during `DreamDroid`'s
 * `super.onCreate()`, before the pre-Room profile import runs.
 */
@Singleton
class ProfileRepository @Inject constructor(private val store: ProfileStore) {
    private val _current = MutableStateFlow<Profile?>(null)
    val current: StateFlow<Profile?> = _current.asStateFlow()

    private val _switches = MutableSharedFlow<Profile>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
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

    /**
     * Replaces the in-memory current profile (the edit path). Locations and tags stay.
     * Device-info XML is dropped when connection settings changed, so the next check
     * talks to the edited receiver instead of reusing the old one's answer.
     */
    @Synchronized
    fun setCurrent(profile: Profile) {
        val previous = _current.value
        if (previous != null && !profile.hasSameSettings(previous)) {
            setDeviceInfo(profile, null)
        }
        _current.value = profile
    }

    /**
     * Activates the saved profile [id] and remembers it as the active profile.
     * False when there is no such row.
     */
    fun setCurrent(id: Int, forceEvent: Boolean = false): Boolean {
        xmlDump = store.xmlDebug()
        val activated = activate(id, forceEvent)
        if (activated) {
            store.setActiveId(id)
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

    /** All saved profiles. */
    fun profiles(): List<Profile> = store.profiles()

    fun profile(id: Int): Profile? = store.profile(id)

    /** The remembered active profile id, else the live one. Null when neither is set. */
    fun activeProfileId(): Int? = store.activeId().takeIf { it > 0 } ?: current.value?.id

    /**
     * Inserts [profile] and sets its id, or updates it when it already has one. An
     * updated active profile replaces [current] without a switch event (the edit path).
     */
    fun save(profile: Profile) {
        val id = profile.id ?: 0
        if (id > 0) {
            store.update(profile)
            if (id == current.value?.id) {
                setCurrent(profile)
            }
        } else {
            profile.id = store.add(profile).toInt()
        }
    }

    /**
     * Deletes [profile] and its offline cache. Deleting the active profile activates
     * the first remaining one, or forgets the active profile when none is left.
     */
    fun delete(profile: Profile) {
        val deletedId = profile.id
        val wasCurrent = deletedId != null && deletedId == current.value?.id
        store.delete(profile)
        if (!wasCurrent) {
            return
        }
        val next = store.profiles().firstOrNull { it.id != null && it.id != deletedId }
        if (next != null) {
            setCurrent(next.id!!, forceEvent = true)
        } else {
            store.clearActiveId()
            setCurrent(Profile.getDefault())
        }
    }

    fun ensureCurrent(): Boolean {
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
        return setCurrent(first, forceEvent = true)
    }

    fun loadCurrent() {
        val profileId = store.activeId()
        val active = current.value
        if (active != null && profileId > 0 && active.id == profileId) {
            return
        }
        soleSeededDemo(store.profiles())?.let { store.delete(it) }
        if (store.profiles().isEmpty()) {
            val candidate = store.legacyProfile()
            if (!candidate.matchesSeededDemo()) {
                val newId = store.add(candidate).toInt()
                setCurrent(newId, forceEvent = true)
                return
            }
        }
        if (profileId > 0 && setCurrent(profileId)) {
            return
        }
        val first = store.profiles().firstOrNull()?.id
        if (first != null && setCurrent(first)) {
            return
        }
        clearCurrent()
    }

    fun reloadCurrent(): Boolean {
        val id = current.value?.id ?: return false
        return setCurrent(id, forceEvent = true)
    }

    @Synchronized
    fun loadLocations(http: EnigmaHttp): Boolean {
        locationList.clear()
        locationsFromReceiver = false
        var gotLoc = false
        val parsed = http.fetchStringList(URIStore.LOCATIONS, "e2location")
        if (parsed != null) {
            locationList.addAll(parsed)
            gotLoc = true
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
        val parsed = http.fetchStringList(URIStore.TAGS, "e2tag")
        if (parsed != null) {
            tagList.addAll(parsed)
            gotTags = true
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
}

/**
 * Where profiles and the active-profile choice persist: Room and the default
 * preferences in the app, memory in tests.
 */
interface ProfileStore {
    fun profiles(): List<Profile>

    fun profile(id: Int): Profile?

    fun add(profile: Profile): Long

    fun update(profile: Profile)

    /** Deletes the row and the offline cache kept for it. */
    fun delete(profile: Profile)

    /** The remembered active profile id, or -1. */
    fun activeId(): Int

    fun setActiveId(id: Int)

    fun clearActiveId()

    /** The debug setting that logs receiver XML. */
    fun xmlDebug(): Boolean

    /** The single-receiver settings of dreamDroid 1.x, as an unsaved profile. */
    fun legacyProfile(): Profile
}

/**
 * Profiles in Room; the active id and legacy settings in the default preferences. Deleting
 * a profile drops its use-driven cache through [ServiceRepository], which depends on
 * [ProfileRepository] and so is [Lazy] here.
 */
class RoomProfileStore @Inject constructor(
    private val database: AppDatabase,
    @param:ApplicationContext private val context: Context,
    private val services: Lazy<ServiceRepository>
) : ProfileStore {
    private val dao: ProfileDaoBlocking
        get() = ProfileDaoBlocking(database.profileDao())

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    override fun profiles(): List<Profile> = dao.getProfiles()

    override fun profile(id: Int): Profile? = dao.getProfile(id)

    override fun add(profile: Profile): Long = dao.addProfile(profile)

    override fun update(profile: Profile) {
        dao.updateProfile(profile)
    }

    override fun delete(profile: Profile) {
        dao.deleteProfile(profile)
        val id = profile.id ?: return
        runBlocking(Dispatchers.IO) { services.get().clearCacheOfDeletedProfile(id) }
    }

    override fun activeId(): Int = preferences.getInt(DreamDroid.CURRENT_PROFILE, -1)

    override fun setActiveId(id: Int) {
        preferences.edit().putInt(DreamDroid.CURRENT_PROFILE, id).apply()
    }

    override fun clearActiveId() {
        preferences.edit().remove(DreamDroid.CURRENT_PROFILE).apply()
    }

    override fun xmlDebug(): Boolean = preferences.getBoolean(DreamDroid.PREFS_KEY_XML_DEBUG, false)

    override fun legacyProfile(): Profile {
        val sp = preferences
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
}

private fun EnigmaHttp.fetchStringList(uri: String, itemTag: String): List<String>? =
    (fetch(uri) as? EnigmaHttpResult.Success)?.let { StringListParser.parse(it.text, itemTag) }
