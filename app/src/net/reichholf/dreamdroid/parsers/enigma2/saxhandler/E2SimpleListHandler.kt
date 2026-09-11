/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

abstract class E2SimpleListHandler(private val mTag: String?) : DefaultHandler() {
    private var inItem: Boolean = false
    private var mItem: String? = null

    @JvmField
    protected var mList: ArrayList<String>? = null

    fun setList(list: ArrayList<String>?) {
        mList = list
    }

    override fun startElement(
        namespaceUri: String?,
        localName: String,
        qName: String?,
        attrs: Attributes?,
    ) {
        if (localName == mTag) {
            inItem = true
            mItem = ""
        }
    }

    override fun endElement(namespaceURI: String?, localName: String, qName: String?) {
        if (localName == mTag) {
            inItem = false
            mList!!.add(mItem!!.trim())
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val value = String(ch, start, length)
        if (inItem) {
            mItem += value
        }
    }
}
