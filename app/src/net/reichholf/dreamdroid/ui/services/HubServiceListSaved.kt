package net.reichholf.dreamdroid.ui.services

fun hubServiceCurrentRefKey(rootRef: String): String = "hub_service_current_ref:$rootRef"

fun hubServiceCurrentNameKey(rootRef: String): String = "hub_service_current_name:$rootRef"

data class HubServiceListSaved(val currentRef: String? = null, val currentName: String? = null)

interface HubServiceListSavedAccess {
    fun getCurrentRef(rootRef: String): String?

    fun setCurrentRef(rootRef: String, currentRef: String)

    fun getCurrentName(rootRef: String): String?

    fun setCurrentName(rootRef: String, currentName: String)
}

class MapHubServiceListSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    HubServiceListSavedAccess {
    override fun getCurrentRef(rootRef: String): String? =
        values[hubServiceCurrentRefKey(rootRef)] as? String

    override fun setCurrentRef(rootRef: String, currentRef: String) {
        values[hubServiceCurrentRefKey(rootRef)] = currentRef
    }

    override fun getCurrentName(rootRef: String): String? =
        values[hubServiceCurrentNameKey(rootRef)] as? String

    override fun setCurrentName(rootRef: String, currentName: String) {
        values[hubServiceCurrentNameKey(rootRef)] = currentName
    }
}

fun readHubServiceListSaved(
    access: HubServiceListSavedAccess,
    rootRef: String
): HubServiceListSaved = HubServiceListSaved(
    currentRef = access.getCurrentRef(rootRef),
    currentName = access.getCurrentName(rootRef)
)

fun HubServiceListSaved.writeTo(access: HubServiceListSavedAccess, rootRef: String) {
    if (currentRef != null) {
        access.setCurrentRef(rootRef, currentRef)
    }
    if (currentName != null) {
        access.setCurrentName(rootRef, currentName)
    }
}

data class HubServiceDrillDown(
    val currentRef: String,
    val currentName: String,
    val historyStep: Pair<String, String>? = null
)

/**
 * Restore the saved directory without a saved history list. A saved ref other
 * than the bouquet root gets one back step to that root.
 */
fun restoreHubServiceDrillDown(
    rootRef: String,
    rootName: String,
    savedRef: String?,
    savedName: String?
): HubServiceDrillDown {
    if (savedRef.isNullOrEmpty() || savedRef == rootRef) {
        return HubServiceDrillDown(currentRef = rootRef, currentName = rootName)
    }
    return HubServiceDrillDown(
        currentRef = savedRef,
        currentName = savedName ?: rootName,
        historyStep = rootRef to rootName
    )
}
