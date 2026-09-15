/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.Request
import net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces.SimpleRequestInterface

/**
 * @author sre
 */
abstract class AbstractSimpleRequestHandler(protected var mUri: String) : SimpleRequestInterface {
    override fun get(shc: SimpleHttpClient?): String? = get(shc, ArrayList())

    override fun get(shc: SimpleHttpClient?, params: ArrayList<NameValuePair>?): String? =
        Request.get(shc!!, mUri, params)
}
