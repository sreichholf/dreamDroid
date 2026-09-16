/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Request
import net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces.SimpleRequestInterface

/**
 * @author sre
 */
abstract class AbstractSimpleRequestHandler(protected var uri: String) : SimpleRequestInterface {
    override fun get(http: EnigmaHttp, params: List<NameValuePair>): String? =
        Request.get(http, uri, params)

    override fun fetch(http: EnigmaHttp, params: List<NameValuePair>): EnigmaHttpResult =
        http.fetch(uri, params)
}
