/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.enigma.PowerState
import net.reichholf.dreamdroid.enigma.PowerStateParser
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * @author sre
 */
class PowerStateRequestHandler : AbstractSimpleRequestHandler(URIStore.POWERSTATE) {
    fun parsePowerState(xml: String?): PowerState {
        if (xml == null) {
            return PowerState()
        }
        return PowerStateParser.parse(xml) ?: PowerState()
    }
}
