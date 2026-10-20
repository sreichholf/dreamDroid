package net.reichholf.dreamdroid.intents

import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.first
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStreamStore
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** Starts the saved live channel once the initial profile check confirms the receiver is online. */
suspend fun autoStartLastLiveStream(
    activity: ComponentActivity,
    settings: SettingsRepository,
    receiver: ReceiverRepository,
    sessions: SessionConnectionHolder,
    onError: (UiText) -> Unit
) {
    if (!settings.current().autoStartLastStream) {
        return
    }
    val status = sessions.status.first {
        !it.checking && (it.session != null || it.lastFailure != null)
    }
    if (
        status.session != ConnectionStatus.Session.Online ||
        activity.isFinishing ||
        !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    ) {
        return
    }
    val lastStream = LastLiveStreamStore(
        PreferenceManager.getDefaultSharedPreferences(activity)
    ).getForActiveProfile() ?: return

    when (val stream = receiver.liveStream(lastStream.reference)) {
        is LiveStream.Ready -> {
            if (
                activity.isFinishing ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                return
            }
            try {
                activity.startActivity(
                    IntentFactory.getStreamServiceIntent(
                        activity,
                        stream,
                        lastStream.title,
                        lastStream.bouquetRef,
                        ServiceNowNext(
                            serviceReference = lastStream.reference,
                            serviceName = lastStream.title
                        )
                    )
                )
            } catch (_: ActivityNotFoundException) {
                onError(UiText.Resource(R.string.missing_stream_player))
            }
        }

        is LiveStream.Failed -> onError(stream.message)
    }
}
