package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.helpers.NameValuePair

data class ServiceListLoadResult(
    val success: Boolean,
    val services: List<Service>,
    val errorText: String?
)

/**
 * Load typed service list without a Fragment owner.
 * Null fetch is failure. Empty 200 stays an empty list.
 */
suspend fun loadServiceList(context: Context, params: List<NameValuePair>): ServiceListLoadResult {
    val response = EnigmaClient().getServices(params)
    val success = response.value != null
    val services = response.value ?: emptyList()
    val errorText = if (success) null else response.error.contentError(context)
    return ServiceListLoadResult(success, services, errorText)
}
