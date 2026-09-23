package net.reichholf.dreamdroid.ui.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HubServiceListSavedTest {
    @Test
    fun absentSnapshotReadsNullWithoutWriting() {
        val values = mutableMapOf<String, Any>()
        val access = MapHubServiceListSavedAccess(values)
        val saved = readHubServiceListSaved(access, "1:7:1:root")
        assertNull(saved.currentRef)
        assertNull(saved.currentName)
        assertFalse(values.containsKey(hubServiceCurrentRefKey("1:7:1:root")))
    }

    @Test
    fun keysAreNamespacedPerBouquetRoot() {
        val values = mutableMapOf<String, Any>()
        val access = MapHubServiceListSavedAccess(values)
        HubServiceListSaved(currentRef = "1:0:1:tv", currentName = "ARD").writeTo(
            access,
            "1:7:1:tv"
        )
        HubServiceListSaved(currentRef = "1:0:2:radio", currentName = "WDR").writeTo(
            access,
            "1:7:2:radio"
        )
        assertEquals(
            "1:0:1:tv",
            readHubServiceListSaved(access, "1:7:1:tv").currentRef
        )
        assertEquals(
            "1:0:2:radio",
            readHubServiceListSaved(access, "1:7:2:radio").currentRef
        )
    }

    @Test
    fun restoreMissingRefStaysAtRoot() {
        val restored = restoreHubServiceDrillDown(
            rootRef = "1:7:1:root",
            rootName = "Favourites",
            savedRef = null,
            savedName = null
        )
        assertEquals("1:7:1:root", restored.currentRef)
        assertEquals("Favourites", restored.currentName)
        assertNull(restored.historyStep)
    }

    @Test
    fun restoreAtRootHasNoHistoryStep() {
        val restored = restoreHubServiceDrillDown(
            rootRef = "1:7:1:root",
            rootName = "Favourites",
            savedRef = "1:7:1:root",
            savedName = "Favourites"
        )
        assertEquals("1:7:1:root", restored.currentRef)
        assertEquals("Favourites", restored.currentName)
        assertNull(restored.historyStep)
    }

    @Test
    fun restoreNestedRefPushesTheRootAsOneHistoryStep() {
        val restored = restoreHubServiceDrillDown(
            rootRef = "1:7:1:root",
            rootName = "Favourites",
            savedRef = "1:7:1:providers",
            savedName = "Providers"
        )
        assertEquals("1:7:1:providers", restored.currentRef)
        assertEquals("Providers", restored.currentName)
        assertEquals("1:7:1:root" to "Favourites", restored.historyStep)
    }
}
