package net.reichholf.dreamdroid.ui.nav

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.helpers.setOrRemove

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

fun readPhoneNavStateBag(handle: SavedStateHandle): PhoneNavStateBag = PhoneNavStateBag(
    startRoute = handle.get<String>(PhoneNavSavedKeys.START_ROUTE),
    pickRequestCodes = handle.get<IntArray>(PhoneNavSavedKeys.PICK_REQUEST_CODES)?.toList()
        ?: emptyList()
)

fun PhoneNavStateBag.writePlain(handle: SavedStateHandle) {
    handle.setOrRemove(PhoneNavSavedKeys.START_ROUTE, startRoute)
    handle[PhoneNavSavedKeys.PICK_REQUEST_CODES] = pickRequestCodes.toIntArray()
}
