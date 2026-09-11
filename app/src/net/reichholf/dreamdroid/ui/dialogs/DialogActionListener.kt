package net.reichholf.dreamdroid.ui.dialogs

/**
 * Callback for in-composition dialog actions that previously routed through
 * [net.reichholf.dreamdroid.fragment.dialogs.ActionDialog].
 */
fun interface DialogActionListener {
    fun onDialogAction(action: Int, details: Any?, dialogTag: String?)
}
