/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.enigma.Volume
import net.reichholf.dreamdroid.enigma.VolumeParser
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * @author sre
 */
class VolumeRequestHandler : AbstractSimpleRequestHandler(URIStore.VOLUME) {
    fun parseVolume(xml: String?): Volume {
        if (xml == null) {
            return Volume()
        }
        return VolumeParser.parse(xml) ?: Volume()
    }
}
