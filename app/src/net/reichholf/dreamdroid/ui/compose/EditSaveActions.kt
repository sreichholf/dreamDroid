package net.reichholf.dreamdroid.ui.compose

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction

/** Phone create/edit top bar: Save, plus Delete when editing an existing item. */
fun saveAndDeleteActions(
    saveLabel: String,
    deleteLabel: String,
    canDelete: Boolean,
    actionsEnabled: Boolean = true,
    onSave: () -> Unit,
    onDelete: () -> Unit
): List<ShellTopBarAction> = buildList {
    add(
        ShellTopBarAction(
            id = Statics.ITEM_SAVE,
            label = saveLabel,
            enabled = actionsEnabled,
            onClick = onSave
        )
    )
    if (canDelete) {
        add(
            ShellTopBarAction(
                id = Statics.ITEM_DELETE,
                label = deleteLabel,
                iconRes = R.drawable.ic_action_delete,
                enabled = actionsEnabled,
                onClick = onDelete
            )
        )
    }
}
