package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

data class EventListLoadResult(
    val success: Boolean,
    val events: List<Event>,
    val errorText: String?,
)

/**
 * Phase 2.7f: load typed EPG event lists without a Fragment owner.
 * Success is "!http.hasError()" — empty lists are success.
 */
suspend fun loadEventList(
    context: Context,
    params: List<NameValuePair>,
    uri: String = URIStore.EPG_SERVICE,
): EventListLoadResult {
    val http = SimpleHttpClient.getInstance()
    val events = EnigmaClient(http).getEvents(params, uri)
    val success = !http.hasError()
    val errorText = if (success) {
        null
    } else {
        context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
    }
    return EventListLoadResult(success, events, errorText)
}

/**
 * Phase 2.2h: load typed EPG event lists via coroutines (no executor / runBlocking).
 */
fun Fragment.launchEventListLoad(
    params: List<NameValuePair>,
    uri: String = URIStore.EPG_SERVICE,
    onResult: (success: Boolean, events: List<Event>, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadEventList(requireContext(), params, uri)
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.events, result.errorText)
    }
}
