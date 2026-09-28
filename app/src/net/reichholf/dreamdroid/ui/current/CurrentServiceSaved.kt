package net.reichholf.dreamdroid.ui.current

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.setOrRemove

object CurrentServiceSavedKeys {
    const val CURRENT = "current_service"
    const val ITEM = "current_item"
    const val READY = "current_service_ready"
    const val PROFILE_ID = "current_profile_id"
}

data class CurrentServiceSaved(
    val current: CurrentService? = null,
    val item: Event? = null,
    val ready: Boolean = false,
    val profileId: Int? = null
)

/**
 * Snapshot [CurrentServiceViewModel] restores for [currentProfileId].
 * [rememberSaveable] was keyed by profile, so a different id drops the snapshot.
 */
fun readCurrentServiceSaved(handle: SavedStateHandle, currentProfileId: Int): CurrentServiceSaved {
    val saved = CurrentServiceSaved(
        current = handle.get<CurrentService>(CurrentServiceSavedKeys.CURRENT),
        item = handle.get<Event>(CurrentServiceSavedKeys.ITEM),
        ready = handle.get<Boolean>(CurrentServiceSavedKeys.READY) ?: false,
        profileId = handle.get<Int>(CurrentServiceSavedKeys.PROFILE_ID)
    )
    if (!shouldRestoreCurrentService(saved.profileId, currentProfileId)) {
        return CurrentServiceSaved()
    }
    return saved
}

fun CurrentServiceSaved.writeTo(handle: SavedStateHandle) {
    handle.setOrRemove(CurrentServiceSavedKeys.CURRENT, current)
    handle.setOrRemove(CurrentServiceSavedKeys.ITEM, item)
    handle[CurrentServiceSavedKeys.READY] = ready
    handle.setOrRemove(CurrentServiceSavedKeys.PROFILE_ID, profileId)
}

/** Missing [savedProfileId] means there is no snapshot to restore. */
fun shouldRestoreCurrentService(savedProfileId: Int?, currentProfileId: Int): Boolean =
    savedProfileId != null && savedProfileId == currentProfileId
