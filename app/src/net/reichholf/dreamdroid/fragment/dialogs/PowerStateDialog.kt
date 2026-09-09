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
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.dialogs.PowerChoiceItem
import net.reichholf.dreamdroid.ui.dialogs.bindPowerStateScreen

/**
 * Drawer power-control dialog (Compose). Selection finishes with the same
 * [Statics.ITEM_*] ids [SimpleChoiceDialog] used, so [NavigationHelper] still
 * runs `setPowerState`. Video overlay keeps [SimpleChoiceDialog].
 */
class PowerStateDialog : ActionDialog() {
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val host = this
		val items = listOf(
			PowerChoiceItem(Statics.ITEM_TOGGLE_STANDBY, getString(R.string.standby)),
			PowerChoiceItem(Statics.ITEM_RESTART_GUI, getString(R.string.restart_gui)),
			PowerChoiceItem(Statics.ITEM_REBOOT, getString(R.string.reboot)),
			PowerChoiceItem(Statics.ITEM_SHUTDOWN, getString(R.string.shutdown)),
		)
		val composeView = ComposeView(requireContext()).apply {
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT,
			)
			setViewTreeLifecycleOwner(host)
			setViewTreeViewModelStoreOwner(host)
			setViewTreeSavedStateRegistryOwner(host)
			bindPowerStateScreen(items) { item ->
				finishDialog(item.id, null)
			}
		}
		return MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.powercontrol)
			.setView(composeView)
			.setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
			.create()
	}

	companion object {
		@JvmStatic
		fun newInstance(): PowerStateDialog = PowerStateDialog()
	}
}
