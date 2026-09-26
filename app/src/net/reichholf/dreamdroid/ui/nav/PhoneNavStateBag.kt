package net.reichholf.dreamdroid.ui.nav

object PhoneNavSavedKeys {
    const val START_ROUTE = "phone_nav_start_route"
    const val PICK_REQUEST_CODES = "phone_nav_pick_request_codes"
}

data class PhoneNavStateBag(
    val startRoute: String? = null,
    val pickRequestCodes: List<Int> = emptyList()
) {
    fun hasSavedStartRoute(): Boolean = startRoute != null
}

interface PhoneNavPlainAccess {
    fun contains(key: String): Boolean

    fun getString(key: String): String?

    fun putString(key: String, value: String?)

    fun getIntList(key: String): List<Int>?

    fun putIntList(key: String, value: List<Int>?)
}

class MapPhoneNavPlainAccess : PhoneNavPlainAccess {
    private val strings = mutableMapOf<String, String>()
    private val intLists = mutableMapOf<String, List<Int>>()

    override fun contains(key: String): Boolean =
        strings.containsKey(key) || intLists.containsKey(key)

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
}

fun readPhoneNavStateBag(access: PhoneNavPlainAccess): PhoneNavStateBag = PhoneNavStateBag(
    startRoute = access.getString(PhoneNavSavedKeys.START_ROUTE),
    pickRequestCodes = access.getIntList(PhoneNavSavedKeys.PICK_REQUEST_CODES) ?: emptyList()
)

fun PhoneNavStateBag.writePlain(access: PhoneNavPlainAccess) {
    access.putString(PhoneNavSavedKeys.START_ROUTE, startRoute)
    access.putIntList(PhoneNavSavedKeys.PICK_REQUEST_CODES, pickRequestCodes)
}
