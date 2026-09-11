/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import org.xml.sax.Attributes

class E2SleepTimerHandler : E2SimpleHandler() {
    private var inSleeptimer = false
    private var inEnabled = false
    private var inMinutes = false
    private var inAction = false
    private var inText = false

    override fun startElement(
        namespaceUri: String?,
        localName: String,
        qName: String?,
        attrs: Attributes?,
    ) {
        if (localName == TAG_E2SLEEPTIMER) {
            inSleeptimer = true
        } else if (inSleeptimer) {
            when (localName) {
                TAG_E2ENABLED -> inEnabled = true
                TAG_E2MINUTES -> inMinutes = true
                TAG_E2ACTION -> inAction = true
                TAG_E2TEXT -> inText = true
            }
        }
    }

    override fun endElement(namespaceURI: String?, localName: String, qName: String?) {
        if (localName == TAG_E2SLEEPTIMER) {
            inSleeptimer = false
        } else if (inSleeptimer) {
            when (localName) {
                TAG_E2ENABLED -> inEnabled = false
                TAG_E2MINUTES -> inMinutes = false
                TAG_E2ACTION -> inAction = false
                TAG_E2TEXT -> inText = false
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val value = String(ch, start, length)
        when {
            inEnabled -> mResult!!.putOrConcat(SleepTimer.KEY_ENABLED, value)
            inMinutes -> mResult!!.putOrConcat(SleepTimer.KEY_MINUTES, value)
            inAction -> mResult!!.putOrConcat(SleepTimer.KEY_ACTION, value)
            inText -> mResult!!.putOrConcat(SleepTimer.KEY_TEXT, value)
        }
    }

    companion object {
        @JvmStatic
        protected val TAG_E2SLEEPTIMER: String = "e2sleeptimer"
        @JvmStatic
        protected val TAG_E2ENABLED: String = "e2enabled"
        @JvmStatic
        protected val TAG_E2MINUTES: String = "e2minutes"
        @JvmStatic
        protected val TAG_E2ACTION: String = "e2action"
        @JvmStatic
        protected val TAG_E2TEXT: String = "e2text"
    }
}
