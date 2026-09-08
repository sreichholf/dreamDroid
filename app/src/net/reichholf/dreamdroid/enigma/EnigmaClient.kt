package net.reichholf.dreamdroid.enigma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import java.util.ArrayList

class EnigmaClient(private val http: SimpleHttpClient) {
    suspend fun getServices(params: List<NameValuePair> = emptyList()): List<Service> {
        return withContext(Dispatchers.IO) {
            val requestParams = ArrayList(params)
            if (!http.fetchPageContent(URIStore.SERVICES, requestParams)) {
                emptyList()
            } else {
                ServiceParser.parse(http.pageContentString)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getServicesBlocking(http: SimpleHttpClient, params: List<NameValuePair>): List<Service> {
            return runBlocking {
                EnigmaClient(http).getServices(params)
            }
        }
    }
}
