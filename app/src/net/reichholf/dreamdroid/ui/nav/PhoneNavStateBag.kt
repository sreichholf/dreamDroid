package net.reichholf.dreamdroid.ui.nav

object PhoneNavSavedKeys {
    const val START_ROUTE = "phone_nav_start_route"
    const val PICK_REQUEST_CODES = "phone_nav_pick_request_codes"

    // Android Bundles. The plain bag does not read or write these keys.
    const val PROFILE_EDIT_ARGS = "phone_nav_profile_edit_args"
    const val PROFILE_EDIT_TAG = "phone_nav_profile_edit_tag"
    const val TIMER_EDIT_ARGS = "phone_nav_timer_edit_args"
    const val TIMER_EDIT_TAG = "phone_nav_timer_edit_tag"
    const val EPG_REF = "phone_nav_epg_ref"
    const val EPG_NAME = "phone_nav_epg_name"
    const val EPG_FOCUSED_REF = "phone_nav_epg_focused_ref"
    const val EPG_TIME_SEC = "phone_nav_epg_time_sec"
}

data class PhoneNavStateBag(
    val startRoute: String? = null,
    val pickRequestCodes: List<Int> = emptyList(),
    val profileEditTag: String = PhoneNavRoutes.PROFILE_EDIT,
    val timerEditTag: String = PhoneNavRoutes.TIMER_EDIT,
    val epgRef: String? = null,
    val epgName: String? = null,
    val epgFocusedRef: String? = null,
    val epgTimeSec: Long? = null
) {
    fun hasSavedStartRoute(): Boolean = startRoute != null
}

interface PhoneNavPlainAccess {
    fun contains(key: String): Boolean

    fun getString(key: String): String?

    fun putString(key: String, value: String?)

    fun getIntList(key: String): List<Int>?

    fun putIntList(key: String, value: List<Int>?)

    fun getLong(key: String): Long?

    fun putLong(key: String, value: Long?)
}

class MapPhoneNavPlainAccess : PhoneNavPlainAccess {
    private val strings = mutableMapOf<String, String>()
    private val intLists = mutableMapOf<String, List<Int>>()
    private val longs = mutableMapOf<String, Long>()

    override fun contains(key: String): Boolean =
        strings.containsKey(key) || intLists.containsKey(key) || longs.containsKey(key)

    override fun getString(key: String): String? = strings[key]

    override fun putString(key: String, value: String?) {
        if (value == null) {
            strings.remove(key)
        } else {
            strings[key] = value
        }
    }

    override fun getIntList(key: String): List<Int>? = intLists[key]

    override fun putIntList(key: String, value: List<Int>?) {
        if (value == null) {
            intLists.remove(key)
        } else {
            intLists[key] = value
        }
    }

    override fun getLong(key: String): Long? = longs[key]

    override fun putLong(key: String, value: Long?) {
        if (value == null) {
            longs.remove(key)
        } else {
            longs[key] = value
        }
    }
}

fun readPhoneNavStateBag(access: PhoneNavPlainAccess): PhoneNavStateBag = PhoneNavStateBag(
    startRoute = access.getString(PhoneNavSavedKeys.START_ROUTE),
    pickRequestCodes = access.getIntList(PhoneNavSavedKeys.PICK_REQUEST_CODES) ?: emptyList(),
    profileEditTag = access.getString(PhoneNavSavedKeys.PROFILE_EDIT_TAG)
        ?: PhoneNavRoutes.PROFILE_EDIT,
    timerEditTag = access.getString(PhoneNavSavedKeys.TIMER_EDIT_TAG)
        ?: PhoneNavRoutes.TIMER_EDIT,
    epgRef = access.getString(PhoneNavSavedKeys.EPG_REF),
    epgName = access.getString(PhoneNavSavedKeys.EPG_NAME),
    epgFocusedRef = access.getString(PhoneNavSavedKeys.EPG_FOCUSED_REF),
    epgTimeSec = access.getLong(PhoneNavSavedKeys.EPG_TIME_SEC)
)

fun PhoneNavStateBag.writePlain(access: PhoneNavPlainAccess) {
    access.putString(PhoneNavSavedKeys.START_ROUTE, startRoute)
    access.putIntList(PhoneNavSavedKeys.PICK_REQUEST_CODES, pickRequestCodes)
    access.putString(PhoneNavSavedKeys.PROFILE_EDIT_TAG, profileEditTag)
    access.putString(PhoneNavSavedKeys.TIMER_EDIT_TAG, timerEditTag)
    access.putString(PhoneNavSavedKeys.EPG_REF, epgRef)
    access.putString(PhoneNavSavedKeys.EPG_NAME, epgName)
    access.putString(PhoneNavSavedKeys.EPG_FOCUSED_REF, epgFocusedRef)
    access.putLong(PhoneNavSavedKeys.EPG_TIME_SEC, epgTimeSec)
}
