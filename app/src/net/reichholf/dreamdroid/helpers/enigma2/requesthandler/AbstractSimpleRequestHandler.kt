/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.Request
import net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces.SimpleRequestInterface
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2SimpleHandler

/**
 * @author sre
 */
abstract class AbstractSimpleRequestHandler(
    @JvmField protected var mUri: String,
    private val mHandler: E2SimpleHandler,
) : SimpleRequestInterface {
    override fun get(shc: SimpleHttpClient?): String? = get(shc, ArrayList())

    override fun get(shc: SimpleHttpClient?, params: ArrayList<NameValuePair>?): String? =
        Request.get(shc!!, mUri, params)

    override fun parse(xml: String?, result: ExtendedHashMap?): Boolean {
        val map = result ?: return false
        return if (Request.parse(xml, map, mHandler)) {
            true
        } else {
            map.clear()
            map.putAll(getDefault())
            false
        }
    }

    open override fun getDefault(): ExtendedHashMap = ExtendedHashMap()
}
