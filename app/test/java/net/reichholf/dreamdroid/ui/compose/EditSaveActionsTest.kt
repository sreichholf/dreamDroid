package net.reichholf.dreamdroid.ui.compose

import net.reichholf.dreamdroid.helpers.Statics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditSaveActionsTest {
    @Test
    fun createHasSaveAndOmitsDelete() {
        val actions = actions(canDelete = false)
        assertEquals(listOf(Statics.ITEM_SAVE), actions.map { it.id })
        assertTrue(actions.single().enabled)
        assertNull(actions.single().iconRes)
    }

    @Test
    fun editHasSaveAndDelete() {
        val actions = actions(canDelete = true)
        assertEquals(listOf(Statics.ITEM_SAVE, Statics.ITEM_DELETE), actions.map { it.id })
        assertEquals("Delete", actions[1].label)
    }

    @Test
    fun mutatingDisablesSaveAndDelete() {
        val actions = actions(canDelete = true, actionsEnabled = false)
        assertFalse(actions.any { it.enabled })
    }

    @Test
    fun clicksReachTheirCallbacks() {
        val clicked = mutableListOf<String>()
        val actions = saveAndDeleteActions(
            saveLabel = "Save",
            deleteLabel = "Delete",
            canDelete = true,
            onSave = { clicked += "save" },
            onDelete = { clicked += "delete" }
        )
        actions.forEach { it.onClick() }
        assertEquals(listOf("save", "delete"), clicked)
    }

    private fun actions(canDelete: Boolean, actionsEnabled: Boolean = true) = saveAndDeleteActions(
        saveLabel = "Save",
        deleteLabel = "Delete",
        canDelete = canDelete,
        actionsEnabled = actionsEnabled,
        onSave = {},
        onDelete = {}
    )
}
