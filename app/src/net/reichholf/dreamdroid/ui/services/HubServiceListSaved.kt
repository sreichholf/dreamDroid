package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle

fun hubServiceCurrentRefKey(rootRef: String): String = "hub_service_current_ref:$rootRef"

fun hubServiceCurrentNameKey(rootRef: String): String = "hub_service_current_name:$rootRef"

data class HubServiceListSaved(val currentRef: String? = null, val currentName: String? = null)

fun readHubServiceListSaved(handle: SavedStateHandle, rootRef: String): HubServiceListSaved =
    HubServiceListSaved(
        currentRef = handle.get<String>(hubServiceCurrentRefKey(rootRef)),
        currentName = handle.get<String>(hubServiceCurrentNameKey(rootRef))
    )

fun HubServiceListSaved.writeTo(handle: SavedStateHandle, rootRef: String) {
    if (currentRef != null) {
        handle[hubServiceCurrentRefKey(rootRef)] = currentRef
    }
    if (currentName != null) {
        handle[hubServiceCurrentNameKey(rootRef)] = currentName
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
