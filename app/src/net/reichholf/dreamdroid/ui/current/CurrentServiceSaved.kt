package net.reichholf.dreamdroid.ui.current

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.helpers.setOrRemove

object CurrentServiceSavedKeys {
    const val CURRENT = "current_service"
    const val PROFILE_ID = "current_profile_id"
}

/** The last-good current service of profile [profileId]. */
data class CurrentServiceSaved(val current: CurrentService? = null, val profileId: Int? = null)

/** The snapshot [CurrentServiceViewModel] restores; empty when another profile saved it. */
fun readCurrentServiceSaved(handle: SavedStateHandle, currentProfileId: Int): CurrentServiceSaved {
    val saved = CurrentServiceSaved(
        current = handle.get<CurrentService>(CurrentServiceSavedKeys.CURRENT),
        profileId = handle.get<Int>(CurrentServiceSavedKeys.PROFILE_ID)
    )
    if (!shouldRestoreCurrentService(saved.profileId, currentProfileId)) {
        return CurrentServiceSaved()
    }
    return saved
}

fun CurrentServiceSaved.writeTo(handle: SavedStateHandle) {
    handle.setOrRemove(CurrentServiceSavedKeys.CURRENT, current)
    handle.setOrRemove(CurrentServiceSavedKeys.PROFILE_ID, profileId)
}

/** Missing [savedProfileId] means there is no snapshot to restore. */
fun shouldRestoreCurrentService(savedProfileId: Int?, currentProfileId: Int): Boolean =
    savedProfileId != null && savedProfileId == currentProfileId
