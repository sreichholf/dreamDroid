/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.enigma.StringListParser
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * @author sre
 */
object Request {
    fun get(shc: SimpleHttpClient, uri: String): String? = get(shc, uri, ArrayList())

    fun get(shc: SimpleHttpClient, uri: String, params: ArrayList<NameValuePair>?): String? {
        val p = params ?: ArrayList()
        if (shc.fetchPageContent(uri, p)) {
            return shc.pageContentString
        }
        return null
    }

    fun getBytes(shc: SimpleHttpClient, uri: String, params: ArrayList<NameValuePair>?): ByteArray {
        val p = params ?: ArrayList()
        if (shc.fetchPageContent(uri, p)) {
            return shc.bytes
        }
        return ByteArray(0)
    }

    fun getBytes(shc: SimpleHttpClient, uri: String?): ByteArray {
        if (shc.fetchPageContent(uri!!)) {
            return shc.bytes
        }
        return ByteArray(0)
    }

    fun parseList(xml: String?, list: ArrayList<String>?, itemTag: String): Boolean {
        if (xml == null || list == null) {
            return false
        }
        val parsed = StringListParser.parse(xml, itemTag) ?: return false
        list.addAll(parsed)
        return true
    }
}
