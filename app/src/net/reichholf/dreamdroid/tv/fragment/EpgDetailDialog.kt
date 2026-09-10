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
import net.reichholf.dreamdroid.fragment.dialogs.AbstractDialog
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.ui.epg.EpgDetailScreen
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContent
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * TV fullscreen EPG detail. Reuses phone [EpgDetailScreen] under [DreamDroidTheme]
 * with actions hidden (Phase 3.1b).
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val event = requireArguments().getSerializable(Event::class.java.simpleName) as Event
        val minutesShort = getString(R.string.minutes_short)
        val content = event.toEpgDetailContent(false, minutesShort)
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
        val event = requireArguments().getSerializable(Event::class.java.simpleName) as Event
        val minutesShort = getString(R.string.minutes_short)
        val content = event.toEpgDetailContent(false, minutesShort) ?: return super.onCreateView(
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
        @JvmStatic
        fun newInstance(epg: Event): EpgDetailDialog {
            val args = Bundle()
            args.putSerializable(Event::class.java.simpleName, epg)
            val fragment = EpgDetailDialog()
            fragment.arguments = args
            return fragment
        }
    }
}
