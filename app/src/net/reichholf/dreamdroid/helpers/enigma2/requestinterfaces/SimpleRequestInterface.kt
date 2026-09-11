/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces

import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import java.util.ArrayList

/**
 * @author sre
 */
interface SimpleRequestInterface {
    fun get(shc: SimpleHttpClient?): String?
    fun get(shc: SimpleHttpClient?, params: ArrayList<NameValuePair>?): String?
    fun parse(xml: String?, result: ExtendedHashMap?): Boolean
    fun getDefault(): ExtendedHashMap
}
