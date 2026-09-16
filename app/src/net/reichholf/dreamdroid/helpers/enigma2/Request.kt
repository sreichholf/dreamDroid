/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.enigma.StringListParser
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * @author sre
 */
object Request {
    fun fetch(
        http: EnigmaHttp,
        uri: String,
        params: List<NameValuePair> = emptyList()
    ): EnigmaHttpResult = http.fetch(uri, params)

    fun get(http: EnigmaHttp, uri: String): String? = get(http, uri, emptyList())

    fun get(http: EnigmaHttp, uri: String, params: List<NameValuePair>?): String? {
        val p = params ?: emptyList()
        return when (val result = http.fetch(uri, p)) {
            is EnigmaHttpResult.Success -> result.text
            is EnigmaHttpResult.Failure -> null
        }
    }

    fun getBytes(http: EnigmaHttp, uri: String, params: List<NameValuePair>?): ByteArray {
        val p = params ?: emptyList()
        return when (val result = http.fetch(uri, p)) {
            is EnigmaHttpResult.Success -> result.bytes
            is EnigmaHttpResult.Failure -> ByteArray(0)
        }
    }

    fun getBytes(http: EnigmaHttp, uri: String): ByteArray = getBytes(http, uri, emptyList())

    fun parseList(xml: String?, list: ArrayList<String>?, itemTag: String): Boolean {
        if (xml == null || list == null) {
            return false
        }
        val parsed = StringListParser.parse(xml, itemTag) ?: return false
        list.addAll(parsed)
        return true
    }
}
