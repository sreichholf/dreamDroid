package net.reichholf.dreamdroid.ui.current

import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentServiceLoadGateTest {
    @Test
    fun staleReloadDoesNotWriteLastGoodOrStrip() {
        val gate = CurrentServiceLoadGate()
        val staleGeneration = gate.beginLoad()
        val freshGeneration = gate.beginLoad()
        val stale = CurrentService(service = Service("1:0:1:1", "Old box"))
        val fresh = CurrentService(service = Service("1:0:1:2", "New box"))

        gate.applySuccess(staleGeneration, profileId = 4, next = stale)
        assertNull(gate.visible(4))

        gate.applySuccess(freshGeneration, profileId = 4, next = fresh)
        assertEquals("New box", gate.visible(4)?.service?.name)

        gate.applySuccess(staleGeneration, profileId = 4, next = stale)
        assertEquals("New box", gate.visible(4)?.service?.name)
    }

    @Test
    fun lastGoodIsHiddenForAnotherProfile() {
        val gate = CurrentServiceLoadGate()
        val generation = gate.beginLoad()
        val boxA = CurrentService(service = Service("1:0:1:1", "Box A"))
        gate.applySuccess(generation, profileId = 7, next = boxA)

        assertEquals("Box A", gate.visible(7)?.service?.name)
        assertNull(gate.visible(8))
        assertFalse(currentServiceCanStream(gate.visible(8)))
    }

    @Test
    fun emptyPayloadDoesNotReplaceLastGoodOrEnableStream() {
        val gate = CurrentServiceLoadGate()
        val first = gate.beginLoad()
        val lastGood = CurrentService(service = Service("1:0:1:1", "Box"))
        assertTrue(gate.applySuccess(first, profileId = 1, next = lastGood))
        assertTrue(currentServiceCanStream(gate.visible(1)))

        val failed = gate.beginLoad()
        assertFalse(gate.applySuccess(failed, profileId = 1, next = CurrentService()))
        assertEquals("Box", gate.visible(1)?.service?.name)
        assertTrue(currentServiceCanStream(gate.visible(1)))
        assertTrue(gate.isCurrent(failed))
    }

    @Test
    fun failedGenerationWithoutLastGoodCannotStream() {
        val gate = CurrentServiceLoadGate()
        val generation = gate.beginLoad()
        assertTrue(gate.isCurrent(generation))
        assertNull(gate.visible(3))
        assertFalse(currentServiceCanStream(gate.visible(3)))
        assertFalse(currentServiceCanStream(null))
        assertFalse(currentServiceCanStream(CurrentService()))
        assertFalse(
            currentServiceCanStream(CurrentService(service = Service("", "Name only")))
        )
    }
}
