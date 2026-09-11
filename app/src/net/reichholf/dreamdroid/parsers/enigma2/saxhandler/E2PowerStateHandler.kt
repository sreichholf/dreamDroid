/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

import net.reichholf.dreamdroid.helpers.enigma2.PowerState
import org.xml.sax.Attributes

class E2PowerStateHandler : E2SimpleHandler() {
    private var inState: Boolean = false

    override fun startElement(
        namespaceUri: String?,
        localName: String,
        qName: String?,
        attrs: Attributes?,
    ) {
        if (localName == TAG_E2INSTANDBY) {
            inState = true
        }
    }

    override fun endElement(namespaceURI: String?, localName: String, qName: String?) {
        if (localName == TAG_E2INSTANDBY) {
            inState = false
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val value = String(ch, start, length)
        if (inState) {
            if ("false" == value.trim()) {
                mResult!!.put(PowerState.KEY_IN_STANDBY, true)
            } else if ("true" == value.trim()) {
                mResult!!.put(PowerState.KEY_IN_STANDBY, false)
            }
        }
    }

    companion object {
        @JvmStatic
        protected val TAG_E2INSTANDBY: String = "e2instandby"
    }
}
