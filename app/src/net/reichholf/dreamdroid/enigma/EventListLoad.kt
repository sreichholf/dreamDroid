package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

data class EventListLoadResult(
    val success: Boolean,
    val events: List<Event>,
    val errorText: String?
)

/**
 * Load typed EPG event lists without a Fragment owner.
 * Null fetch is failure. Empty 200 stays an empty list.
 */
suspend fun loadEventList(
    context: Context,
    params: List<NameValuePair>,
    uri: String = URIStore.EPG_SERVICE
): EventListLoadResult {
    val response = EnigmaClient().getEvents(params, uri)
    val success = response.value != null
    val events = response.value ?: emptyList()
    val errorText = if (success) null else response.error.contentError(context)
    return EventListLoadResult(success, events, errorText)
}
