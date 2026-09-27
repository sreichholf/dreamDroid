package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellTopBarControllerTest {
    private val controller = ShellTopBarController()

    @Test
    fun newestBindingWins() {
        val a = controller.claim()
        controller.bind(a, listOf(action("A")))
        val b = controller.claim()
        controller.bind(b, listOf(action("B")))
        assertEquals(listOf("B"), labels())
    }

    @Test
    fun leavingDestinationUpdatesDoNotTakeTheBarBack() {
        val a = controller.claim()
        controller.bind(a, listOf(action("A")))
        val b = controller.claim()
        controller.bind(b, listOf(action("B")))
        controller.update(a, listOf(action("A2")))
        assertEquals(listOf("B"), labels())
        controller.release(a)
        assertEquals(listOf("B"), labels())
    }

    @Test
    fun cancelledBackPreviewRestoresTheShownDestination() {
        // Timer edit (b) is shown; a back gesture composes the hub (preview) and is cancelled.
        val b = controller.claim()
        controller.bind(b, listOf(action("Save")))
        val preview = controller.claim()
        controller.bind(preview, listOf(action("Clean up")))
        controller.release(preview)
        assertEquals(listOf("Save"), labels())
        controller.update(b, listOf(action("Save"), action("Delete")))
        assertEquals(listOf("Save", "Delete"), labels())
    }

    @Test
    fun releasingEveryBindingClearsTheBar() {
        val a = controller.claim()
        controller.bind(a, listOf(action("A")))
        controller.release(a)
        assertTrue(controller.actions.isEmpty())
        controller.update(a, listOf(action("A")))
        assertTrue(controller.actions.isEmpty())
    }

    private fun labels() = controller.actions.map { it.label }

    private fun action(label: String) = ShellTopBarAction(id = label.hashCode(), label = label) {}
}
