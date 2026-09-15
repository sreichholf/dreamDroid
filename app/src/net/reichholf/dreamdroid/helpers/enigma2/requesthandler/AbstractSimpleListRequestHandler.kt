/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.Request

/**
 * @author sre
 */
abstract class AbstractSimpleListRequestHandler(
    private val mUri: String,
    private val itemTag: String
) {
    fun getList(shc: SimpleHttpClient): String? = Request.get(shc, mUri)

    fun parseList(xml: String?, list: ArrayList<String>): Boolean =
        Request.parseList(xml, list, itemTag)
}
