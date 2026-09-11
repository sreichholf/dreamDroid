/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import org.xml.sax.helpers.DefaultHandler

/**
 * @author sre
 */
open class E2SimpleHandler : DefaultHandler() {
    @JvmField
    protected var mResult: ExtendedHashMap? = null

    fun setMap(map: ExtendedHashMap?) {
        mResult = map
    }
}
