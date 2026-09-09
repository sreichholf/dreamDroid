package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.PowerState
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.helpers.enigma2.Volume
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.PowerStateRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SleepTimerRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler
import java.util.ArrayList

/**
 * Phase 2.2l: volume / power / sleeptimer mutations via coroutines (no executor).
 * Dedicated [SimpleHttpClient] per call (same as the former AsyncTasks).
 */

fun LifecycleOwner.launchVolumeSetLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, volume: ExtendedHashMap) -> Unit,
): Job {
    return lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val pair = withContext(Dispatchers.IO) {
            val handler = VolumeRequestHandler()
            val xml = handler.get(http, ArrayList(params))
            if (xml != null) {
                val volume = ExtendedHashMap()
                handler.parse(xml, volume)
                if (volume.getString(Volume.KEY_CURRENT) != null) {
                    true to volume
                } else {
                    false to ExtendedHashMap()
                }
            } else {
                false to ExtendedHashMap()
            }
        }
        onResult(pair.first, pair.second)
    }
}

fun LifecycleOwner.launchPowerStateSetLoad(
    state: String,
    context: Context,
    onResult: (success: Boolean, result: ExtendedHashMap, errorText: String?) -> Unit,
): Job {
    return lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val triple = withContext(Dispatchers.IO) {
            val handler = PowerStateRequestHandler()
            val xml = handler.get(http, PowerState.getStateParams(state))
            if (xml != null) {
                val result = ExtendedHashMap()
                handler.parse(xml, result)
                Triple(true, result, null as String?)
            } else {
                val err = errorText(context, http)
                Triple(false, ExtendedHashMap(), err)
            }
        }
        onResult(triple.first, triple.second, triple.third)
    }
}

fun LifecycleOwner.launchSleepTimerLoad(
    params: List<NameValuePair>,
    openDialog: Boolean,
    context: Context,
    onResult: (success: Boolean, result: ExtendedHashMap, openDialog: Boolean, errorText: String?) -> Unit,
): Job {
    return lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val outcome = withContext(Dispatchers.IO) {
            val handler = SleepTimerRequestHandler()
            val xml = handler.get(http, ArrayList(params))
            if (xml != null) {
                val result = ExtendedHashMap()
                handler.parse(xml, result)
                if (result.getString(SleepTimer.KEY_ENABLED) != null) {
                    Triple(true, result, null as String?)
                } else {
                    Triple(false, ExtendedHashMap(), errorText(context, http))
                }
            } else {
                Triple(false, ExtendedHashMap(), errorText(context, http))
            }
        }
        onResult(outcome.first, outcome.second, openDialog, outcome.third)
    }
}

private fun errorText(context: Context, http: SimpleHttpClient): String {
    return if (http.hasError()) {
        context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
    } else {
        context.getString(R.string.get_content_error)
    }
}
