package net.reichholf.dreamdroid.ui.epg

import android.app.ProgressDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory

/**
 * Shared EPG detail-sheet actions for bouquet / service / search / hub destinations.
 * Phase 2.1g-ii-d: opens an in-composition [EpgDetailModalSheet] (no DialogFragment).
 */
class EpgEventDialogSession {
    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    var currentItem: ExtendedHashMap? = null
    var detailEvent by mutableStateOf<Event?>(null)
        private set
    private var progress: ProgressDialog? = null

    fun showDetail(event: Event) {
        currentItem = EpgListMapper.toExtendedHashMap(event)
        detailEvent = event
    }

    fun dismissDetail() {
        detailEvent = null
    }

    fun dismissProgress() {
        progress?.takeIf { it.isShowing }?.dismiss()
        progress = null
    }

    fun onSetTimer() {
        val host = hostFragment ?: return
        val ctx = context ?: return
        val item = currentItem ?: return
        dismissProgress()
        progress = ProgressDialog.show(ctx, "", ctx.getText(R.string.saving), true)
        host.launchSimpleResultLoad(
            TimerAddByEventIdRequestHandler(),
            Timer.getEventIdParams(item),
        ) { _, result, http ->
            dismissProgress()
            var toastText = ctx.getText(R.string.get_content_error).toString()
            val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
            when {
                !stateText.isNullOrEmpty() -> toastText = stateText
                http.hasError() -> toastText = http.getErrorText(ctx).orEmpty()
            }
            Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show()
        }
    }

    fun onEditTimer() {
        val host = hostFragment ?: return
        val item = currentItem ?: return
        host.navigateToTimerEdit(Timer.createByEvent(item), true)
    }

    fun onFindSimilar() {
        val host = hostFragment ?: return
        val item = currentItem ?: return
        host.navigateToEpgSearch(item.getString(EventKeys.KEY_EVENT_TITLE))
    }

    fun onImdb() {
        val ctx = context ?: return
        val item = currentItem ?: return
        val activity = ctx as? AppCompatActivity ?: return
        IntentFactory.queryIMDb(activity, item)
    }
}

/** Renders [EpgEventDialogSession.detailEvent] as a Material 3 modal sheet when set. */
@Composable
fun EpgEventDetailSheetHost(session: EpgEventDialogSession) {
    val event = session.detailEvent ?: return
    val minutesShort = stringResource(R.string.minutes_short)
    val content = event.toEpgDetailContent(minutesShort) ?: run {
        session.dismissDetail()
        return
    }
    EpgDetailModalSheet(
        content = content,
        onDismiss = { session.dismissDetail() },
        onSetTimer = { session.onSetTimer() },
        onEditTimer = { session.onEditTimer() },
        onImdb = { session.onImdb() },
        onSimilar = { session.onFindSimilar() },
    )
}
