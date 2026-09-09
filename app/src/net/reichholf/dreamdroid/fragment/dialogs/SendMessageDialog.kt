package net.reichholf.dreamdroid.fragment.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.dialogs.SendMessageUiState
import net.reichholf.dreamdroid.ui.dialogs.bindSendMessageScreen

/**
 * Send on-screen message. Compose Material 3 form; Send still goes to
 * [SendMessageDialogActionListener] on the activity.
 */
class SendMessageDialog : AbstractDialog() {
    private val uiState = SendMessageUiState()

    interface SendMessageDialogActionListener {
        fun onSendMessage(text: String, type: String, timeout: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = this
        val composeView = ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            bindSendMessageScreen(uiState)
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.send_message)
            .setView(composeView)
            .setPositiveButton(R.string.send) { _, _ ->
                (activity as SendMessageDialogActionListener).onSendMessage(
                    uiState.message,
                    uiState.typeIndex.toString(),
                    uiState.timeout,
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()
    }

    companion object {
        @JvmStatic
        fun newInstance(): SendMessageDialog = SendMessageDialog()
    }
}
