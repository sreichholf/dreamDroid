package net.reichholf.dreamdroid.enigma

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * Volume mutations via coroutines. Power and sleep timer live on
 * [net.reichholf.dreamdroid.data.ReceiverRepository].
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
