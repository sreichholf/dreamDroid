package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.PowerState as PowerStateKeys
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.PowerStateRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SleepTimerRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler

/**
 * Volume / power / sleeptimer mutations via coroutines.
 */

fun LifecycleOwner.launchVolumeSetLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, volume: Volume) -> Unit
): Job = lifecycleScope.launch {
    val http = EnigmaHttp()
    val pair = withContext(Dispatchers.IO) {
        val handler = VolumeRequestHandler()
        when (val fetched = handler.fetch(http, params)) {
            is EnigmaHttpResult.Success -> {
                val volume = VolumeParser.parse(fetched.text) ?: Volume()
                if (volume.current != null) {
                    true to volume
                } else {
                    false to Volume()
                }
            }

            is EnigmaHttpResult.Failure -> false to Volume()
        }
    }
    onResult(pair.first, pair.second)
}

fun LifecycleOwner.launchPowerStateSetLoad(
    state: String,
    context: Context,
    onResult: (success: Boolean, result: PowerState, errorText: String?) -> Unit
): Job = lifecycleScope.launch {
    val http = EnigmaHttp()
    val triple = withContext(Dispatchers.IO) {
        val handler = PowerStateRequestHandler()
        when (val fetched = handler.fetch(http, PowerStateKeys.getStateParams(state))) {
            is EnigmaHttpResult.Success ->
                Triple(true, PowerStateParser.parse(fetched.text) ?: PowerState(), null as String?)

            is EnigmaHttpResult.Failure ->
                Triple(false, PowerState(), fetched.error.contentError(context))
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
    val http = EnigmaHttp()
    val outcome = withContext(Dispatchers.IO) {
        val handler = SleepTimerRequestHandler()
        when (val fetched = handler.fetch(http, params)) {
            is EnigmaHttpResult.Success -> {
                val result = SleepTimerParser.parse(fetched.text) ?: SleepTimer()
                if (result.enabled != null) {
                    Triple(true, result, null as String?)
                } else {
                    Triple(false, SleepTimer(), context.getString(R.string.get_content_error))
                }
            }

            is EnigmaHttpResult.Failure ->
                Triple(false, SleepTimer(), fetched.error.contentError(context))
        }
    }
    onResult(outcome.first, outcome.second, openDialog, outcome.third)
}
