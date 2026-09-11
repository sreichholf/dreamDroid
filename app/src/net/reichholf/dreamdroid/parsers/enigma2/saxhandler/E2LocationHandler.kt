/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

class E2LocationHandler : E2SimpleListHandler(TAG_E2LOCATION) {
    companion object {
        const val TAG_E2LOCATION: String = "e2location"
    }
}
