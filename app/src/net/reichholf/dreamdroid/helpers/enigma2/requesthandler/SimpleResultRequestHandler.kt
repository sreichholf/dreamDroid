/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler

import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.SimpleResultParser
import net.reichholf.dreamdroid.helpers.Python

open class SimpleResultRequestHandler(uri: String) : AbstractSimpleRequestHandler(uri) {
    fun parseSimpleResult(xml: String?): SimpleResult {
        val parsed = xml?.let { SimpleResultParser.parse(it) }
        if (parsed == null) {
            return SimpleResult(state = Python.FALSE, stateText = null)
        }
        return parsed
    }
}
