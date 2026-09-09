package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2 beachhead: load typed device info via coroutines (no executor / runBlocking).
 * Call from a fragment that already has a view ([Fragment.getViewLifecycleOwner]).
 *
 * Uses a dedicated [SimpleHttpClient] per load (same as the old GetDeviceInfoTask),
 * not the fragment helper’s shared client — [SimpleHttpClient] is not thread-safe and
 * Job.cancel does not abort in-flight HttpURLConnection I/O.
 */
fun Fragment.launchDeviceInfoLoad(
    onResult: (success: Boolean, info: DeviceInfo?, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val info = EnigmaClient(http).getDeviceInfo()
        if (!isAdded) {
            return@launch
        }
        val success = info != null && !info.isEmpty()
        val errorText = when {
            success -> null
            http.hasError() ->
                getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
            else -> getString(R.string.error_parsing)
        }
        onResult(success, info, errorText)
    }
}
