/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.enigma2.Request

/**
 * @author sre
 */
abstract class AbstractSimpleListRequestHandler(
    private val uri: String,
    private val itemTag: String
) {
    fun getList(http: EnigmaHttp): String? = Request.get(http, uri)

    fun parseList(xml: String?, list: ArrayList<String>): Boolean =
        Request.parseList(xml, list, itemTag)
}
