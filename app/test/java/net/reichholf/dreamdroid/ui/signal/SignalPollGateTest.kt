package net.reichholf.dreamdroid.ui.signal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SignalPollGateTest {
    @Test
    fun stopInvalidatesInFlightGeneration() {
        val gate = SignalPollGate()
        gate.start()
        val generation = gate.nextLoadGeneration()
        assertTrue(gate.isCurrent(generation))
        gate.stop()
        assertFalse(gate.active)
        assertFalse(gate.isCurrent(generation))
    }

    @Test
    fun reloadAfterDisposeDoesNotStayCurrent() {
        val gate = SignalPollGate()
        gate.start()
        val generation = gate.nextLoadGeneration()
        gate.stop()
        // Tail reload() after onDispose must not continue the poll loop.
        assertFalse(gate.isCurrent(generation))
        assertEquals(generation + 1, gate.generation)
    }

    @Test
    fun startAfterStopRequiresANewGeneration() {
        val gate = SignalPollGate()
        gate.start()
        val first = gate.nextLoadGeneration()
        gate.stop()
        gate.start()
        assertFalse(gate.isCurrent(first))
        val second = gate.nextLoadGeneration()
        assertTrue(gate.isCurrent(second))
        assertTrue(second > first)
    }
}
