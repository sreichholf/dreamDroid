package net.reichholf.dreamdroid.ui.current

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.epg.EpgDetailModalSheet
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContentOrUnavailable
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.video.startLiveServiceStream

/**
 * Phase 2.7c: Current Service as a direct Compose NavHost destination.
 * Dialog actions (EPG sheet → timer/IMDb/similar) are registered on [handle].
 * The load and saved model live on [CurrentServiceViewModel]. Whether the EPG
 * sheet is open stays here as [detailEvent].
 *
 * [updateToolbarTitle] is false when hosted in [CurrentServiceSheet] so the hub
 * bouquet title is not overwritten.
 */
@Composable
fun CurrentServiceDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    updateToolbarTitle: Boolean = true,
    viewModel: CurrentServiceViewModel = viewModel()
) {
    val context = LocalContext.current
    var detailEvent by remember { mutableStateOf<Event?>(null) }
    val session = remember(viewModel) { CurrentServiceSession(viewModel) }
    session.handle = handle
    session.context = context

    val title = viewModel.toolbarTitle
    val error = viewModel.errorText
    LaunchedEffect(title, updateToolbarTitle) {
        if (updateToolbarTitle) {
            (context as? AppCompatActivity)?.title = title
        }
    }
    LaunchedEffect(error) {
        if (!error.isNullOrEmpty()) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.start()
    }

    DisposableEffect(handle, session) {
        handle.composeDialogActionListener = session
        onDispose {
            if (handle.composeDialogActionListener === session) {
                handle.composeDialogActionListener = null
            }
        }
    }

    fun showEpgDetail(event: Event?) {
        if (event == null) {
            return
        }
        viewModel.rememberCurrentItem(event)
        detailEvent = event
    }

    fun streamService() {
        val current = viewModel.current
        if (!currentServiceCanStream(current)) {
            return
        }
        handle.runOnlineOnly {
            val service = current?.service
            val ref = service?.reference.orEmpty()
            val name = service?.name.orEmpty()
            val activity = context as AppCompatActivity
            activity.startLiveServiceStream(activity, ref) {
                activity.startActivity(
                    IntentFactory.getStreamServiceIntent(activity, ref, name)
                )
            }
        }
    }

    fun onNowOrNextOrStream(action: Int) {
        if (!viewModel.ready) {
            return
        }
        when (action) {
            Statics.ITEM_NOW -> showEpgDetail(viewModel.current?.now)
            Statics.ITEM_NEXT -> showEpgDetail(viewModel.current?.next)
            Statics.ITEM_STREAM -> streamService()
        }
    }

    DreamDroidPullRefresh(
        refreshing = viewModel.refreshing,
        onRefresh = { viewModel.reload() },
        enabled = true,
        modifier = modifier
    ) {
        CurrentServiceScreen(
            state = viewModel.uiState,
            onNowClick = { onNowOrNextOrStream(Statics.ITEM_NOW) },
            onNextClick = { onNowOrNextOrStream(Statics.ITEM_NEXT) },
            onStream = { onNowOrNextOrStream(Statics.ITEM_STREAM) }
        )
    }

    detailEvent?.let { event ->
        val minutesShort = stringResource(R.string.minutes_short)
        val unavailable = stringResource(R.string.not_available)
        val content = event.toEpgDetailContentOrUnavailable(minutesShort, unavailable)
        EpgDetailModalSheet(
            content = content,
            onDismiss = { detailEvent = null },
            onSetTimer = {
                session.onDialogAction(Statics.ACTION_SET_TIMER, null, null)
            },
            onEditTimer = {
                session.onDialogAction(Statics.ACTION_EDIT_TIMER, null, null)
            },
            onImdb = {
                session.onDialogAction(Statics.ACTION_IMDB, null, null)
            },
            onSimilar = {
                session.onDialogAction(Statics.ACTION_FIND_SIMILAR, null, null)
            }
        )
    }

    IndeterminateProgressHost(viewModel.progress)
}

private class CurrentServiceSession(private val viewModel: CurrentServiceViewModel) :
    DialogActionListener {
    var handle: PhoneNavHandle? = null
    var context: android.content.Context? = null

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        val ctx = context ?: return
        val host = handle ?: return
        when (action) {
            Statics.ACTION_SET_TIMER -> {
                if (viewModel.progress != null) {
                    return
                }
                if (viewModel.currentItem == null) {
                    return
                }
                host.runOnlineOnly {
                    viewModel.addTimer()
                }
            }

            Statics.ACTION_EDIT_TIMER -> {
                val event = viewModel.currentItem ?: return
                host.runOnlineOnly {
                    host.navigateToTimerEdit(Timer.createByEvent(event), true)
                }
            }

            Statics.ACTION_FIND_SIMILAR -> {
                val query = viewModel.currentItem?.title
                host.navigateToEpgSearch(query)
            }

            Statics.ACTION_IMDB -> {
                val event = viewModel.currentItem ?: return
                val activity = ctx as? AppCompatActivity ?: return
                IntentFactory.queryIMDb(activity, event)
            }
        }
    }
}
