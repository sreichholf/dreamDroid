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
import net.reichholf.dreamdroid.ui.dialogs.bindConnectionErrorScreen

/**
 * Connection-error dialog (Compose). Keeps the same action ids as the old
 * AlertDialog so [net.reichholf.dreamdroid.activities.MainActivity] routing
 * stays unchanged.
 */
class ConnectionErrorDialog : ActionDialog() {
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val host = this
		val args = requireArguments()
		val title = args.getString(KEY_TITLE) ?: getString(R.string.error)
		val message = args.getString(KEY_TEXT).orEmpty()

		val composeView = ComposeView(requireContext()).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT,
			)
			setViewTreeLifecycleOwner(host)
			setViewTreeViewModelStoreOwner(host)
			setViewTreeSavedStateRegistryOwner(host)
			bindConnectionErrorScreen(
				message = message,
				onPositive = { finishDialog(ACTION_POSITIVE, null) },
				onEditProfile = { finishDialog(ACTION_EDIT_PROFILE, null) },
			)
		}

		return MaterialAlertDialogBuilder(requireContext())
			.setTitle(title)
			.setView(composeView)
			.setCancelable(false)
			.create()
	}

	companion object {
		private const val KEY_TITLE = "title"
		private const val KEY_TEXT = "text"

		const val ACTION_POSITIVE = 0x00
		const val ACTION_EDIT_PROFILE = 0x01

		@JvmStatic
		fun newInstance(title: String?, text: String?): ConnectionErrorDialog {
			val args = Bundle()
			args.putString(KEY_TITLE, title)
			args.putString(KEY_TEXT, text)
			val dialog = ConnectionErrorDialog()
			dialog.arguments = args
			return dialog
		}
	}
}
