package net.reichholf.dreamdroid.ui.current

import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event

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

interface CurrentServiceSavedAccess {
    fun getCurrent(): CurrentService?

    fun setCurrent(current: CurrentService?)

    fun getItem(): Event?

    fun setItem(item: Event?)

    fun getReady(): Boolean

    fun setReady(ready: Boolean)

    fun getProfileId(): Int?

    fun setProfileId(profileId: Int?)
}

class MapCurrentServiceSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    CurrentServiceSavedAccess {
    override fun getCurrent(): CurrentService? =
        values[CurrentServiceSavedKeys.CURRENT] as CurrentService?

    override fun setCurrent(current: CurrentService?) {
        if (current == null) {
            values.remove(CurrentServiceSavedKeys.CURRENT)
        } else {
            values[CurrentServiceSavedKeys.CURRENT] = current
        }
    }

    override fun getItem(): Event? = values[CurrentServiceSavedKeys.ITEM] as Event?

    override fun setItem(item: Event?) {
        if (item == null) {
            values.remove(CurrentServiceSavedKeys.ITEM)
        } else {
            values[CurrentServiceSavedKeys.ITEM] = item
        }
    }

    override fun getReady(): Boolean = values[CurrentServiceSavedKeys.READY] as? Boolean ?: false

    override fun setReady(ready: Boolean) {
        values[CurrentServiceSavedKeys.READY] = ready
    }

    override fun getProfileId(): Int? = values[CurrentServiceSavedKeys.PROFILE_ID] as? Int

    override fun setProfileId(profileId: Int?) {
        if (profileId == null) {
            values.remove(CurrentServiceSavedKeys.PROFILE_ID)
        } else {
            values[CurrentServiceSavedKeys.PROFILE_ID] = profileId
        }
    }
}

/**
 * Snapshot [CurrentServiceViewModel] restores for [currentProfileId].
 * [rememberSaveable] was keyed by profile, so a different id drops the snapshot.
 */
fun readCurrentServiceSaved(
    access: CurrentServiceSavedAccess,
    currentProfileId: Int
): CurrentServiceSaved {
    val saved = CurrentServiceSaved(
        current = access.getCurrent(),
        item = access.getItem(),
        ready = access.getReady(),
        profileId = access.getProfileId()
    )
    if (!shouldRestoreCurrentService(saved.profileId, currentProfileId)) {
        return CurrentServiceSaved()
    }
    return saved
}

fun CurrentServiceSaved.writeTo(access: CurrentServiceSavedAccess) {
    access.setCurrent(current)
    access.setItem(item)
    access.setReady(ready)
    access.setProfileId(profileId)
}

/** Missing [savedProfileId] means there is no snapshot to restore. */
fun shouldRestoreCurrentService(savedProfileId: Int?, currentProfileId: Int): Boolean =
    savedProfileId != null && savedProfileId == currentProfileId
