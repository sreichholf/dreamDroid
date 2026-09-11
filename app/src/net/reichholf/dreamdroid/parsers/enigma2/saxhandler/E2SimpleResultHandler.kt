/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import org.xml.sax.Attributes

class E2SimpleResultHandler : E2SimpleHandler() {
    private var inState: Boolean = false
    private var inStateText: Boolean = false

    override fun startElement(
        namespaceUri: String?,
        localName: String,
        qName: String?,
        attrs: Attributes?,
    ) {
        if (localName == TAG_E2STATE || localName == TAG_E2RESULT) {
            inState = true
        } else if (localName == TAG_E2STATETEXT || localName == TAG_E2RESULTTEXT) {
            inStateText = true
        }
    }

    override fun endElement(namespaceURI: String?, localName: String, qName: String?) {
        if (localName == TAG_E2STATE || localName == TAG_E2RESULT) {
            inState = false
        } else if (localName == TAG_E2STATETEXT || localName == TAG_E2RESULTTEXT) {
            inStateText = false
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val value = String(ch, start, length)
        if (inState) {
            mResult!!.putOrConcat(SimpleResult.KEY_STATE, value)
        } else if (inStateText) {
            mResult!!.putOrConcat(SimpleResult.KEY_STATE_TEXT, value)
        }
    }

    companion object {
        @JvmStatic
        protected val TAG_E2RESULTTEXT: String = "e2resulttext"
        @JvmStatic
        protected val TAG_E2STATETEXT: String = "e2statetext"
        @JvmStatic
        protected val TAG_E2RESULT: String = "e2result"
        @JvmStatic
        protected val TAG_E2STATE: String = "e2state"
    }
}
