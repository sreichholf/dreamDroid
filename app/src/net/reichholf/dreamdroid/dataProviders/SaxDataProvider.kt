/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.dataProviders

import net.reichholf.dreamdroid.parsers.GenericSaxParser
import org.xml.sax.helpers.DefaultHandler

/**
 * @author sreichholf
 */
class SaxDataProvider(dp: GenericSaxParser) : AbstractDataProvider(dp) {
    fun setParser(dp: GenericSaxParser) {
        mParser = dp
    }

    fun setHandler(handler: DefaultHandler?) {
        getParser().setHandler(handler)
    }

    override fun getParser(): GenericSaxParser = mParser as GenericSaxParser
}
