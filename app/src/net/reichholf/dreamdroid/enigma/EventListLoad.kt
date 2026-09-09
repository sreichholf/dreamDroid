package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * Phase 2.2h: load typed EPG event lists via coroutines (no executor / runBlocking).
 * Uses a dedicated [SimpleHttpClient] per load (same as GetEventListTask).
 * Success is "!http.hasError()" — empty lists are success (matches the former task).
 *
 * @param uri [URIStore.EPG_SERVICE], [URIStore.EPG_BOUQUET], or [URIStore.EPG_SEARCH]
 */
fun Fragment.launchEventListLoad(
    params: List<NameValuePair>,
    uri: String = URIStore.EPG_SERVICE,
    onResult: (success: Boolean, events: List<Event>, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val events = EnigmaClient(http).getEvents(params, uri)
        if (!isAdded) {
            return@launch
        }
        val success = !http.hasError()
        val errorText = if (success) {
            null
        } else {
            getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
        }
        onResult(success, events, errorText)
    }
}
