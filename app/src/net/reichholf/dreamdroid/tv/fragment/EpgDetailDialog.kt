package net.reichholf.dreamdroid.tv.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.fragment.dialogs.AbstractDialog
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Event as HashEvent
import net.reichholf.dreamdroid.ui.epg.EpgDetailContent
import net.reichholf.dreamdroid.ui.epg.EpgDetailScreen
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContent
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * TV fullscreen EPG detail. Reuses phone [EpgDetailScreen] under [DreamDroidTheme]
 * with actions hidden (Phase 3.1b). Prefers typed [Event]; hash kept for legacy callers.
 */
class EpgDetailDialog : AbstractDialog() {

    init {
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dreamdroid_FullscreenDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun detailContent(minutesShort: String): EpgDetailContent? {
        val args = requireArguments()
        val typed = args.getSerializable(ARG_TYPED_EVENT) as? Event
        if (typed != null) {
            return typed.toEpgDetailContent(minutesShort)
        }
        val hash = args.getSerializable(ARG_HASH_EVENT) as? ExtendedHashMap ?: return null
        return hash.toEpgDetailContent(false, minutesShort)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = detailContent(getString(R.string.minutes_short))
        if (content != null) {
            return super.onCreateDialog(savedInstanceState)
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.not_available)
            .setMessage(R.string.no_epg_available)
            .setPositiveButton(R.string.close) { _, _ -> dismiss() }
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val content = detailContent(getString(R.string.minutes_short)) ?: return super.onCreateView(
            inflater,
            container,
            savedInstanceState,
        )
        val host = this
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DreamDroidTheme {
                    EpgDetailScreen(
                        content = content,
                        onSetTimer = {},
                        onEditTimer = {},
                        onImdb = {},
                        onSimilar = {},
                        modifier = Modifier.fillMaxSize(),
                        showActions = false,
                        bodyHeightCap = null,
                    )
                }
            }
        }
    }

    companion object {
        private const val ARG_TYPED_EVENT = "typedEvent"
        private const val ARG_HASH_EVENT = "Event"

        @JvmStatic
        fun newInstance(epg: Event): EpgDetailDialog {
            val args = Bundle()
            args.putSerializable(ARG_TYPED_EVENT, epg)
            val fragment = EpgDetailDialog()
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun newInstance(epg: HashEvent): EpgDetailDialog {
            val args = Bundle()
            args.putSerializable(ARG_HASH_EVENT, epg)
            val fragment = EpgDetailDialog()
            fragment.arguments = args
            return fragment
        }
    }
}
