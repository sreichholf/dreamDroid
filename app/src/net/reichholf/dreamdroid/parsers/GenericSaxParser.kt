/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers

import android.util.Log
import net.reichholf.dreamdroid.dataProviders.interfaces.DataParser
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.regex.Pattern
import javax.xml.parsers.SAXParserFactory

/**
 * @author sreichholf
 */
class GenericSaxParser : DataParser {
    private var mHandler: DefaultHandler? = null
    private var mError: Boolean = false
    private var mErrorText: String? = null

    constructor() {
        mError = false
    }

    constructor(h: DefaultHandler?) {
        mHandler = h
        mError = false
    }

    fun setHandler(h: DefaultHandler?) {
        mHandler = h
    }

    fun getHandler(): DefaultHandler? = mHandler

    protected fun stripNonValidXMLCharacters(input: String, aggressive: Boolean): String {
        return if (aggressive) {
            sControlPatternAggressive.matcher(input).replaceAll("").replace("&nbsp;", " ")
        } else {
            stripControlCharacters(input).replace("\u008A", "\n").replace("&nbsp;", " ")
        }
    }

    /*
     * this is based on https://github.com/GreyCat/java-string-benchmark/blob/master/src/ru/greycat/algorithms/strip/RatchetFreak2EdStaub1GreyCat1.java
     * and is about a zillion lightyears faster than replaceAll... (defeats noticable lag between load finish and parse finish)
     */
    fun stripControlCharacters(s: String): String {
        val length = s.length
        val oldChars = CharArray(length + 1)
        java.lang.String(s).getChars(0, length, oldChars, 0)
        oldChars[length] = '\u0000' // avoiding explicit bound check in while

        var newLen = -1
        // find first non-printable,
        // if there are none it ends on the null char I appended
        while (true) {
            ++newLen
            val ch = oldChars[newLen]
            if (!(ch > ' ' || Character.isWhitespace(ch))) break
        }

        for (j in newLen until length) {
            val ch = oldChars[j]
            if (ch > ' ' || Character.isWhitespace(ch)) {
                oldChars[newLen] = ch // the while avoids repeated overwriting here when newLen==j
                newLen++
            }
        }
        return String(oldChars, 0, newLen)
    }

    protected fun parse(input: String, isRetry: Boolean): Boolean {
        var xml = input
        try {
            xml = stripNonValidXMLCharacters(xml, isRetry)
            mError = false
            mErrorText = null
            val bais = ByteArrayInputStream(xml.toByteArray())
            val `is` = InputSource()
            `is`.byteStream = bais

            val spf = SAXParserFactory.newInstance()
            spf.isValidating = false
            val sp = spf.newSAXParser()

            /* Get the XMLReader of the SAXParser we created. */
            val xr = sp.xmlReader
            /* Create a new ContentHandler and apply it to the XML-Reader */
            xr.contentHandler = mHandler
            xr.parse(`is`)

            return true
        } catch (e: Exception) {
            // TODO Auto-generated catch block
            Log.e(LOG_TAG, e.toString())
            if (isRetry) {
                mError = true
                mErrorText = e.toString()
            } else {
                Log.w(LOG_TAG, "Retrying with aggressive character filtering!")
                return parse(input, true)
            }
        }
        return false
    }

    override fun parse(input: String?): Boolean = parse(input!!, false)

    fun hasError(): Boolean = mError

    fun getErrorText(): String? = mErrorText

    companion object {
        @JvmField
        val LOG_TAG: String = GenericSaxParser::class.java.simpleName

        @JvmField
        val sControlPatternAggressive: Pattern = Pattern.compile("\\p{C}")
    }
}
