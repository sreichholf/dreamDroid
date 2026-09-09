package net.reichholf.dreamdroid.fragment.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.epg.EpgDetailContent
import net.reichholf.dreamdroid.ui.epg.EpgDetailScreen
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContent
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * EPG detail bottom sheet. Compose Material 3 body; parents still own timer/IMDb/similar via
 * [ActionDialog.DialogActionListener]. Typed [Event] preferred; hash + [showNext] kept for
 * hub/video now-next rows until those paths are typed.
 */
class EpgDetailBottomSheet : BottomSheetActionDialog() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val minutesShort = getString(R.string.minutes_short)
        val isNext = args.getBoolean(ARG_SHOW_NEXT, false)
        val content: EpgDetailContent? = when {
            args.containsKey(ARG_TYPED_EVENT) -> {
                val event = args.getSerializable(ARG_TYPED_EVENT) as? Event
                event?.toEpgDetailContent(minutesShort)
            }
            else -> {
                val map = args.getSerializable(ARG_HASH_EVENT) as? ExtendedHashMap
                map?.toEpgDetailContent(isNext, minutesShort)
            }
        }

        if (content == null) {
            return MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.not_available)
                .setMessage(R.string.no_epg_available)
                .setPositiveButton(R.string.close) { _, _ -> dismiss() }
                .create()
        }

        val dialog = super.onCreateDialog(savedInstanceState)
        val host = this
        val composeView = ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DreamDroidTheme {
                    EpgDetailScreen(
                        content = content,
                        onSetTimer = { finishDialog(Statics.ACTION_SET_TIMER, content.isNext) },
                        onEditTimer = { finishDialog(Statics.ACTION_EDIT_TIMER, content.isNext) },
                        onImdb = { finishDialog(Statics.ACTION_IMDB, content.isNext) },
                        onSimilar = { finishDialog(Statics.ACTION_FIND_SIMILAR, content.isNext) },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        val parent = composeView.parent as? View
        if (parent != null) {
            BottomSheetBehavior.from(parent).state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    companion object {
        private const val ARG_TYPED_EVENT = "typedEvent"
        private const val ARG_HASH_EVENT = "Event"
        private const val ARG_SHOW_NEXT = "showNext"

        @JvmStatic
        fun newInstance(event: Event): EpgDetailBottomSheet {
            val args = Bundle()
            args.putSerializable(ARG_TYPED_EVENT, event)
            val fragment = EpgDetailBottomSheet()
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun newInstance(epg: ExtendedHashMap): EpgDetailBottomSheet {
            return newInstance(epg, false)
        }

        @JvmStatic
        fun newInstance(epg: ExtendedHashMap, showNext: Boolean): EpgDetailBottomSheet {
            val args = Bundle()
            args.putSerializable(ARG_HASH_EVENT, epg)
            args.putBoolean(ARG_SHOW_NEXT, showNext)
            val fragment = EpgDetailBottomSheet()
            fragment.arguments = args
            return fragment
        }
    }
}
