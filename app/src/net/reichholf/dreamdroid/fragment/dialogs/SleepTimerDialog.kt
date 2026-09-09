package net.reichholf.dreamdroid.fragment.dialogs

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.dialogs.SleepTimerUiState
import net.reichholf.dreamdroid.ui.dialogs.bindSleepTimerScreen

/**
 * Sleep timer form. Compose Material 3 body; Save still goes to
 * [SleepTimerDialogActionListener] on the activity.
 */
class SleepTimerDialog : AbstractDialog() {
    private lateinit var uiState: SleepTimerUiState

    interface SleepTimerDialogActionListener {
        fun onSetSleepTimer(time: String, action: String, enabled: Boolean)
    }

    private fun initState() {
        @Suppress("UNCHECKED_CAST")
        val timer = (requireArguments().getSerializable(KEY_TIMER) as ExtendedHashMap).clone() as ExtendedHashMap
        var minutes = 90
        try {
            minutes = Integer.parseInt(timer.getString(SleepTimer.KEY_MINUTES))
        } catch (nfe: NumberFormatException) {
            Log.w(TAG, nfe.localizedMessage ?: "")
        }
        val enabled = Python.TRUE == timer.getString(SleepTimer.KEY_ENABLED)
        val action = timer.getString(SleepTimer.KEY_ACTION) ?: SleepTimer.ACTION_STANDBY
        uiState = SleepTimerUiState(minutes, enabled, action)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        initState()
        val host = this
        val composeView = ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            bindSleepTimerScreen(uiState)
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sleeptimer)
            .setView(composeView)
            .setPositiveButton(R.string.save) { _, _ ->
                (activity as SleepTimerDialogActionListener).onSetSleepTimer(
                    uiState.minutes.toString(),
                    uiState.action,
                    uiState.enabled,
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()
    }

    companion object {
        private const val KEY_TIMER = "timer"
        private const val TAG = "SleepTimerDialog"

        @JvmStatic
        fun newInstance(sleepTimer: ExtendedHashMap): SleepTimerDialog {
            val f = SleepTimerDialog()
            val args = Bundle()
            args.putSerializable(KEY_TIMER, sleepTimer)
            f.arguments = args
            return f
        }
    }
}
