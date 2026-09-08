package net.reichholf.dreamdroid.enigma

import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import java.util.ArrayList

class EnigmaClient(private val http: SimpleHttpClient) {
    suspend fun getServices(params: List<NameValuePair> = emptyList()): List<Service> {
        val requestParams = ArrayList(params)
        if (!http.fetchPageContent(URIStore.SERVICES, requestParams)) {
            return emptyList()
        }
        return ServiceParser.parse(http.pageContentString)
    }

    companion object {
        @JvmStatic
        fun of(http: SimpleHttpClient): EnigmaClient {
            return EnigmaClient(http)
        }

        @JvmStatic
        fun getServicesBlocking(http: SimpleHttpClient, params: List<NameValuePair>): List<Service> {
            return runBlocking {
                EnigmaClient(http).getServices(params)
            }
        }
    }
}
