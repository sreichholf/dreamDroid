/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.parsers.GenericSaxParser
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SimpleHandler
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SimpleListHandler

/**
 * @author sre
 */
object Request {
    @JvmStatic
    fun get(shc: SimpleHttpClient, uri: String): String? = get(shc, uri, ArrayList())

    @JvmStatic
    fun get(shc: SimpleHttpClient, uri: String, params: ArrayList<NameValuePair>?): String? {
        val p = params ?: ArrayList()
        if (shc.fetchPageContent(uri, p)) {
            return shc.pageContentString
        }
        return null
    }

    @JvmStatic
    fun getBytes(shc: SimpleHttpClient, uri: String, params: ArrayList<NameValuePair>?): ByteArray {
        val p = params ?: ArrayList()
        if (shc.fetchPageContent(uri, p)) {
            return shc.bytes
        }
        return ByteArray(0)
    }

    @JvmStatic
    fun getBytes(shc: SimpleHttpClient, uri: String?): ByteArray {
        if (shc.fetchPageContent(uri!!)) {
            return shc.bytes
        }
        return ByteArray(0)
    }

    @JvmStatic
    fun parse(xml: String?, result: ExtendedHashMap?, handler: E2SimpleHandler): Boolean {
        val sdp = SaxDataProvider(GenericSaxParser())
        handler.setMap(result)
        sdp.getParser().setHandler(handler)
        return sdp.parse(xml)
    }

    @JvmStatic
    fun parseList(xml: String?, list: ArrayList<String>?, handler: E2SimpleListHandler): Boolean {
        val sdp = SaxDataProvider(GenericSaxParser())
        handler.setList(list)
        sdp.setHandler(handler)
        return sdp.parse(xml)
    }
}
