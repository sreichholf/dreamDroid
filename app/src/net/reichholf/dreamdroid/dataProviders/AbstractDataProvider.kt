/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.dataProviders

import net.reichholf.dreamdroid.dataProviders.interfaces.DataParser

/**
 * @author sreichholf
 */
abstract class AbstractDataProvider(parser: DataParser) {
    @JvmField
    protected var mParser: DataParser = parser

    open fun getParser(): DataParser = mParser

    fun parse(input: String?): Boolean = mParser.parse(input)
}
