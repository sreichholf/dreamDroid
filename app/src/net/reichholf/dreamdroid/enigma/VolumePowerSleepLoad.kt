package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.PowerState as PowerStateKeys
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.PowerStateRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SleepTimerRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler

/**
 * Phase 2.2l: volume / power / sleeptimer mutations via coroutines (no executor).
 * Dedicated [SimpleHttpClient] per call (same as the former AsyncTasks).
 */

fun LifecycleOwner.launchVolumeSetLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, volume: Volume) -> Unit
): Job = lifecycleScope.launch {
    val http = SimpleHttpClient.getInstance()
    val pair = withContext(Dispatchers.IO) {
        val handler = VolumeRequestHandler()
        val xml = handler.get(http, ArrayList(params))
        if (xml != null) {
            val volume = VolumeParser.parse(xml) ?: Volume()
            if (volume.current != null) {
                true to volume
            } else {
                false to Volume()
            }
        } else {
            false to Volume()
        }
    }
    onResult(pair.first, pair.second)
}

fun LifecycleOwner.launchPowerStateSetLoad(
    state: String,
    context: Context,
    onResult: (success: Boolean, result: PowerState, errorText: String?) -> Unit
): Job = lifecycleScope.launch {
    val http = SimpleHttpClient.getInstance()
    val triple = withContext(Dispatchers.IO) {
        val handler = PowerStateRequestHandler()
        val xml = handler.get(http, PowerStateKeys.getStateParams(state))
        if (xml != null) {
            Triple(true, PowerStateParser.parse(xml) ?: PowerState(), null as String?)
        } else {
            Triple(false, PowerState(), errorText(context, http))
        }
    }
    onResult(triple.first, triple.second, triple.third)
}

fun LifecycleOwner.launchSleepTimerLoad(
    params: List<NameValuePair>,
    openDialog: Boolean,
    context: Context,
    onResult: (
        success: Boolean,
        result: SleepTimer,
        openDialog: Boolean,
        errorText: String?
    ) -> Unit
): Job = lifecycleScope.launch {
    val http = SimpleHttpClient.getInstance()
    val outcome = withContext(Dispatchers.IO) {
        val handler = SleepTimerRequestHandler()
        val xml = handler.get(http, ArrayList(params))
        if (xml != null) {
            val result = SleepTimerParser.parse(xml) ?: SleepTimer()
            if (result.enabled != null) {
                Triple(true, result, null as String?)
            } else {
                Triple(false, SleepTimer(), errorText(context, http))
            }
        } else {
            Triple(false, SleepTimer(), errorText(context, http))
        }
    }
    onResult(outcome.first, outcome.second, openDialog, outcome.third)
}

private fun errorText(context: Context, http: SimpleHttpClient): String = if (http.hasError()) {
    context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
} else {
    context.getString(R.string.get_content_error)
}
