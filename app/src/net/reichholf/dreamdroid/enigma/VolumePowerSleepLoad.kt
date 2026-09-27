package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.PowerState as PowerStateKeys

/**
 * Volume / power / sleeptimer mutations via coroutines.
 */

fun LifecycleOwner.launchVolumeSetLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, volume: Volume) -> Unit
): Job = lifecycleScope.launch {
    val volume = EnigmaClient().setVolume(params).value
    if (volume?.current != null) {
        onResult(true, volume)
    } else {
        onResult(false, Volume())
    }
}

data class PowerStateSetOutcome(
    val success: Boolean,
    val powerState: PowerState,
    val errorText: String?
)

suspend fun fetchPowerStateSet(state: String, context: Context): PowerStateSetOutcome {
    val response = EnigmaClient().setPowerState(PowerStateKeys.getStateParams(state))
    val error = response.error
    return if (error == null) {
        PowerStateSetOutcome(
            success = true,
            powerState = response.value ?: PowerState(),
            errorText = null
        )
    } else {
        PowerStateSetOutcome(
            success = false,
            powerState = PowerState(),
            errorText = error.contentError(context)
        )
    }
}

data class SleepTimerLoadOutcome(
    val success: Boolean,
    val timer: SleepTimer,
    val errorText: String?
)

suspend fun fetchSleepTimer(params: List<NameValuePair>, context: Context): SleepTimerLoadOutcome {
    val response = EnigmaClient().sleepTimer(params)
    val timer = response.value
    val error = response.error
    return when {
        error != null ->
            SleepTimerLoadOutcome(
                success = false,
                timer = SleepTimer(),
                errorText = error.contentError(context)
            )

        timer?.enabled != null ->
            SleepTimerLoadOutcome(success = true, timer = timer, errorText = null)

        else ->
            SleepTimerLoadOutcome(
                success = false,
                timer = SleepTimer(),
                errorText = context.getString(R.string.get_content_error)
            )
    }
}
