package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector

/**
 * Phase 2.2m: profile check + device detect via coroutines (no executor).
 * Matches CheckProfileTask / DetectDevicesTask success and progress behavior.
 */

fun LifecycleOwner.launchCheckProfileLoad(
    profile: Profile,
    context: Context,
    onProgress: (state: String) -> Unit,
    onResult: (result: ExtendedHashMap?) -> Unit,
): Job {
    return lifecycleScope.launch {
        onProgress(context.getString(R.string.checking))
        val result = withContext(Dispatchers.IO) {
            CheckProfile.checkProfile(profile, context)
        }
        onResult(result)
    }
}

fun LifecycleOwner.launchDetectDevicesLoad(
    onResult: (profiles: ArrayList<Profile>) -> Unit,
): Job {
    return lifecycleScope.launch {
        val profiles = withContext(Dispatchers.IO) {
            DeviceDetector.getAvailableHosts()
        }
        onResult(profiles)
    }
}
