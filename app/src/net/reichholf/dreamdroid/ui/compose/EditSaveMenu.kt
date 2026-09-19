package net.reichholf.dreamdroid.ui.compose

import android.view.Menu
import android.view.MenuInflater
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics

/** Phone create/edit toolbar: Save, plus Delete when editing an existing item. */
fun MenuInflater.inflateSaveAndDelete(
    menu: Menu,
    canDelete: Boolean,
    actionsEnabled: Boolean = true
) {
    inflate(R.menu.save, menu)
    if (canDelete) {
        inflate(R.menu.edit_delete, menu)
    }
    menu.findItem(Statics.ITEM_SAVE)?.isEnabled = actionsEnabled
    menu.findItem(Statics.ITEM_DELETE)?.isEnabled = actionsEnabled
}
