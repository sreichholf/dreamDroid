package net.reichholf.dreamdroid.multiepg

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The persist decision for a hub tab and a nested folder opened under it. */
class MultiEpgPersistGateTest {
    private val gate = MultiEpgPersistGate(EXCLUDED_TABS)

    @Test
    fun anEmptyTabStripNeverPersists() {
        assertFalse(gate.persist(PROFILE, TAB))
        assertFalse(gate.persist(PROFILE, FOLDER, TAB))
    }

    @Test
    fun aKnownTabIsItsOwnRoot() {
        gate.knowTabs(PROFILE, listOf(TAB, OTHER_TAB))

        assertTrue(gate.persist(PROFILE, TAB))
        assertTrue(gate.persist(PROFILE, OTHER_TAB))
    }

    @Test
    fun aNestedFolderNeedsItsTabRoot() {
        gate.knowTabs(PROFILE, listOf(TAB))

        assertFalse(gate.persist(PROFILE, FOLDER), "a nested folder without a root fails closed")
        assertTrue(gate.persist(PROFILE, FOLDER, TAB), "its tab makes the folder cacheable")
    }

    @Test
    fun anUnknownRootFailsClosed() {
        gate.knowTabs(PROFILE, listOf(TAB))

        assertFalse(gate.persist(PROFILE, FOLDER, OTHER_TAB))
        assertFalse(gate.persist(PROFILE, FOLDER, ""))
        assertFalse(gate.persist(PROFILE, FOLDER, "1:7:1:0:0:0:0:0:0:0:(null)"))
    }

    @Test
    fun anUnknownRootFallsBackToTheRefWhenItIsATab() {
        gate.knowTabs(PROFILE, listOf(TAB))

        // A 1.x default stores an aggregate parent; the tab itself is still cacheable.
        assertTrue(gate.persist(PROFILE, TAB, "1:7:1:0:0:0:0:0:0:0:FROM BOUQUETS ORDER BY bouquet"))
    }

    @Test
    fun anExcludedRootOrRefNeverPersists() {
        gate.knowTabs(PROFILE, listOf(TAB, FOLDER, EXCLUDED, PROVIDERS))

        assertFalse(
            gate.persist(PROFILE, FOLDER, EXCLUDED),
            "a dedicated root is never a cache root"
        )
        assertFalse(gate.persist(PROFILE, EXCLUDED), "a dedicated root is never cacheable itself")
        assertFalse(gate.persist(PROFILE, FOLDER, PROVIDERS))
        assertFalse(gate.persist(PROFILE, PROVIDERS))
    }

    @Test
    fun tabsOfAnotherProfileNeverPersist() {
        gate.knowTabs(PROFILE, listOf(TAB))

        assertFalse(gate.persist(OTHER_PROFILE, TAB))
        gate.forget()
        assertFalse(gate.persist(PROFILE, TAB))
    }

    private companion object {
        const val PROFILE = 1
        const val OTHER_PROFILE = 2
        const val TAB =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val OTHER_TAB =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
        const val FOLDER = "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
        const val PROVIDERS =
            "1:7:1:0:0:0:0:0:0:0:FROM PROVIDERS ORDER BY bouquet"

        /** A dedicated root the hub never caches, like `All services`. */
        const val EXCLUDED =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.all.tv\" ORDER BY bouquet"

        val EXCLUDED_TABS = listOf(EXCLUDED)
    }
}
