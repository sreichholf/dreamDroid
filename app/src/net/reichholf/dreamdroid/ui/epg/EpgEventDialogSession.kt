package net.reichholf.dreamdroid.ui.epg

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.withReadableTimes
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.launchSimpleResultLoad

/**
 * Shared EPG detail-sheet actions for bouquet / service / search / hub destinations.
 * Phase 2.1g-ii-d: opens an in-composition [EpgDetailModalSheet] (no DialogFragment).
 */
class EpgEventDialogSession {
    var handle: PhoneNavHandle? = null
    var context: android.content.Context? = null
    var currentItem: Event? = null
    var detailEvent by mutableStateOf<Event?>(null)
        private set
    var progress by mutableStateOf<IndeterminateProgressState?>(null)

    fun showDetail(event: Event) {
        val display = event.withReadableTimes()
        currentItem = display
        detailEvent = display
    }

    fun dismissDetail() {
        detailEvent = null
    }

    fun dismissProgress() {
        progress = null
    }

    fun onSetTimer() {
        val host = handle ?: return
        val ctx = context ?: return
        val item = currentItem ?: return
        progress = IndeterminateProgressState(message = ctx.getString(R.string.saving))
        host.launchSimpleResultLoad(
            TimerAddByEventIdRequestHandler(),
            Timer.getEventIdParams(item)
        ) { _, result, error ->
            dismissProgress()
            var toastText = ctx.getText(R.string.get_content_error).toString()
            val stateText = result.stateText
            when {
                !stateText.isNullOrEmpty() -> toastText = stateText
                error != null -> toastText = error.resolve(ctx).orEmpty()
            }
            Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show()
        }
    }

    fun onEditTimer() {
        val host = handle ?: return
        val item = currentItem ?: return
        host.navigateToTimerEdit(Timer.createByEvent(item), true)
    }

    fun onFindSimilar() {
        val host = handle ?: return
        val item = currentItem ?: return
        host.navigateToEpgSearch(item.title)
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
    val event = session.detailEvent
    if (event != null) {
        val minutesShort = stringResource(R.string.minutes_short)
        val unavailable = stringResource(R.string.not_available)
        val content = event.toEpgDetailContentOrUnavailable(minutesShort, unavailable)
        EpgDetailModalSheet(
            content = content,
            onDismiss = { session.dismissDetail() },
            onSetTimer = { session.onSetTimer() },
            onEditTimer = { session.onEditTimer() },
            onImdb = { session.onImdb() },
            onSimilar = { session.onFindSimilar() }
        )
    }
    IndeterminateProgressHost(session.progress)
}
