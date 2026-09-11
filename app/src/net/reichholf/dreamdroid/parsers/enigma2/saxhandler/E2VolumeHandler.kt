/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

import net.reichholf.dreamdroid.helpers.enigma2.Volume
import org.xml.sax.Attributes

class E2VolumeHandler : E2SimpleHandler() {
    private var inResult: Boolean = false
    private var inCurrent: Boolean = false
    private var inMuted: Boolean = false

    override fun startElement(
        namespaceUri: String?,
        localName: String,
        qName: String?,
        attrs: Attributes?,
    ) {
        when (localName) {
            TAG_E2RESULT -> inResult = true
            TAG_E2CURRENT -> inCurrent = true
            TAG_E2ISMUTED -> inMuted = true
        }
    }

    override fun endElement(namespaceURI: String?, localName: String, qName: String?) {
        when (localName) {
            TAG_E2RESULT -> inResult = false
            TAG_E2CURRENT -> inCurrent = false
            TAG_E2ISMUTED -> inMuted = false
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val value = String(ch, start, length)
        when {
            inResult -> mResult!!.putOrConcat(Volume.KEY_RESULT, value)
            inCurrent -> mResult!!.putOrConcat(Volume.KEY_CURRENT, value)
            inMuted -> mResult!!.putOrConcat(Volume.KEY_MUTED, value)
        }
    }

    companion object {
        @JvmStatic
        protected val TAG_E2ISMUTED: String = "e2ismuted"
        @JvmStatic
        protected val TAG_E2CURRENT: String = "e2current"
        @JvmStatic
        protected val TAG_E2RESULT: String = "e2result"
    }
}
