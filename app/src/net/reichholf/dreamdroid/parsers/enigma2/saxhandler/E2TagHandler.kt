/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

class E2TagHandler : E2SimpleListHandler("e2tag") {
    companion object {
        @JvmField
        protected val TAG_E2TAG: String = "e2tag"
    }
}
