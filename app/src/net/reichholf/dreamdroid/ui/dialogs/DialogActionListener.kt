package net.reichholf.dreamdroid.ui.dialogs

/**
 * Callback for Compose dialog / sheet actions (power, sleep timer, EPG event, etc.).
 */
fun interface DialogActionListener {
    fun onDialogAction(action: Int, details: Any?, dialogTag: String?)
}
