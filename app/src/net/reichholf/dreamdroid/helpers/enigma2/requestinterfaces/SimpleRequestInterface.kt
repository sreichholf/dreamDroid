/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces

import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * @author sre
 */
interface SimpleRequestInterface {
    fun get(http: EnigmaHttp, params: List<NameValuePair> = emptyList()): String?
    fun fetch(http: EnigmaHttp, params: List<NameValuePair> = emptyList()): EnigmaHttpResult
}
