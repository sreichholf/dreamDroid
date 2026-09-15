package net.reichholf.dreamdroid.enigma

import android.util.Xml
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Shared XmlPullParser entry for Enigma2 XML: sanitize (mild, then aggressive retry),
 * namespaces off, TEXT events left uncoalesced so callers can append split nodes.
 */
internal fun <T> parseEnigmaXml(
    xml: String,
    emptyResult: T,
    onFail: T = emptyResult,
    block: (XmlPullParser) -> T
): T {
    if (xml.isEmpty()) {
        return emptyResult
    }
    return parseSanitized(xml, aggressive = false, block)
        ?: parseSanitized(xml, aggressive = true, block)
        ?: onFail
}

private fun <T> parseSanitized(xml: String, aggressive: Boolean, block: (XmlPullParser) -> T): T? =
    try {
        val parser = newEnigmaPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(XmlInput.sanitize(xml, aggressive)))
        block(parser)
    } catch (_: Exception) {
        null
    }

internal fun newEnigmaPullParser(): XmlPullParser {
    try {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        return parser
    } catch (_: Throwable) {
        return EnigmaXmlPullParser()
    }
}

/** Prefer local/qName, then strip a namespace prefix after the last `:`. */
internal fun XmlPullParser.localTag(): String {
    val prefix = prefix
    val raw = if (!prefix.isNullOrEmpty()) {
        name.orEmpty()
    } else {
        val n = name.orEmpty()
        if (n.isNotEmpty()) n else ""
    }
    val colon = raw.lastIndexOf(':')
    return if (colon >= 0) raw.substring(colon + 1) else raw
}

internal fun String.stripCntrl(): String = replace("\\p{Cntrl}".toRegex(), "")

internal fun XmlPullParser.appendText(target: StringBuilder) {
    val value = text
    if (value != null) {
        target.append(value)
    }
}

/**
 * Minimal [XmlPullParser] for JVM unit tests where [Xml.newPullParser] is a stub.
 * [next] skips comments/PI (so `hello<!--x-->world` yields two TEXT events).
 */
internal class EnigmaXmlPullParser : XmlPullParser {
    private var data: String = ""
    private var pos: Int = 0
    private var event: Int = XmlPullParser.START_DOCUMENT
    private var tagName: String? = null
    private var textValue: String? = null
    private var pendingEndTag: Boolean = false
    private var depth: Int = 0
    private var emptyElement: Boolean = false
    private var line: Int = 1
    private var column: Int = 1
    private val tagStack = ArrayList<String>()

    override fun setFeature(name: String?, state: Boolean) {
        // Namespaces stay off; FEATURE_PROCESS_NAMESPACES is ignored.
    }

    override fun getFeature(name: String?): Boolean = false

    override fun setProperty(name: String?, value: Any?): Unit =
        throw XmlPullParserException("unsupported property $name")

    override fun getProperty(name: String?): Any? = null

    override fun setInput(inputStream: InputStream?, inputEncoding: String?): Unit =
        throw XmlPullParserException("setInput(InputStream) is not used")

    override fun setInput(reader: Reader?) {
        data = reader?.readText() ?: ""
        pos = 0
        event = XmlPullParser.START_DOCUMENT
        tagName = null
        textValue = null
        pendingEndTag = false
        depth = 0
        emptyElement = false
        line = 1
        column = 1
        tagStack.clear()
        if (data.startsWith("\uFEFF")) {
            pos = 1
        }
    }

    override fun getInputEncoding(): String? = "UTF-8"

    override fun defineEntityReplacementText(entityName: String?, replacementText: String?): Unit =
        throw XmlPullParserException("defineEntityReplacementText is not used")

    override fun getNamespaceCount(depth: Int): Int = 0

    override fun getNamespacePrefix(pos: Int): String? = null

    override fun getNamespaceUri(pos: Int): String? = null

    override fun getNamespace(prefix: String?): String? = XmlPullParser.NO_NAMESPACE

    override fun getNamespace(): String = XmlPullParser.NO_NAMESPACE

    override fun getDepth(): Int = depth

    override fun getPositionDescription(): String = "line $line column $column"

    override fun getLineNumber(): Int = line

    override fun getColumnNumber(): Int = column

    override fun isWhitespace(): Boolean {
        val t = textValue ?: return false
        return t.all { it.isWhitespace() }
    }

    override fun getText(): String? = textValue

    override fun getTextCharacters(holderForStartAndLength: IntArray?): CharArray? {
        val t = textValue ?: return null
        if (holderForStartAndLength != null && holderForStartAndLength.size >= 2) {
            holderForStartAndLength[0] = 0
            holderForStartAndLength[1] = t.length
        }
        return t.toCharArray()
    }

    override fun getPrefix(): String? {
        val n = tagName ?: return null
        val colon = n.lastIndexOf(':')
        return if (colon >= 0) n.substring(0, colon) else null
    }

    override fun getName(): String? = tagName

    override fun getAttributeCount(): Int = 0

    override fun getAttributeNamespace(index: Int): String? = null

    override fun getAttributeName(index: Int): String? = null

    override fun getAttributePrefix(index: Int): String? = null

    override fun getAttributeType(index: Int): String = "CDATA"

    override fun isAttributeDefault(index: Int): Boolean = false

    override fun getAttributeValue(index: Int): String? = null

    override fun getAttributeValue(namespace: String?, name: String?): String? = null

    override fun getEventType(): Int = event

    override fun isEmptyElementTag(): Boolean = emptyElement

    override fun next(): Int {
        if (event == XmlPullParser.END_DOCUMENT) {
            return event
        }
        if (pendingEndTag) {
            pendingEndTag = false
            emptyElement = false
            event = XmlPullParser.END_TAG
            popTag()
            textValue = null
            return event
        }
        emptyElement = false
        while (true) {
            if (pos >= data.length) {
                if (tagStack.isNotEmpty()) {
                    throw XmlPullParserException("unclosed tag ${tagStack.last()}")
                }
                event = XmlPullParser.END_DOCUMENT
                tagName = null
                textValue = null
                return event
            }
            if (data[pos] != '<') {
                parseText()
                return event
            }
            when {
                startsWith("<?") -> skipUntil("?>")

                startsWith("<!--") -> skipUntil("-->")

                startsWith("<![CDATA[") -> {
                    parseCdata()
                    return event
                }

                startsWith("<!DOCTYPE") -> skipDoctype()

                startsWith("</") -> {
                    parseEndTag()
                    return event
                }

                else -> {
                    parseStartTag()
                    return event
                }
            }
        }
    }

    override fun nextToken(): Int = next()

    override fun nextTag(): Int {
        var e = next()
        if (e == XmlPullParser.TEXT && isWhitespace()) {
            e = next()
        }
        if (e != XmlPullParser.START_TAG && e != XmlPullParser.END_TAG) {
            throw XmlPullParserException("expected start or end tag")
        }
        return e
    }

    override fun nextText(): String {
        if (event != XmlPullParser.START_TAG) {
            throw XmlPullParserException("nextText() requires START_TAG")
        }
        val e = next()
        val result = if (e == XmlPullParser.TEXT) {
            val t = textValue ?: ""
            next()
            t
        } else {
            ""
        }
        if (event != XmlPullParser.END_TAG) {
            throw XmlPullParserException("nextText() expected END_TAG")
        }
        return result
    }

    override fun require(type: Int, namespace: String?, name: String?) {
        if (event != type || (name != null && tagName != name)) {
            throw XmlPullParserException("require failed")
        }
    }

    private fun startsWith(token: String): Boolean = data.startsWith(token, pos)

    private fun parseStartTag() {
        consume('<')
        tagName = readName()
        skipAttributes()
        textValue = null
        event = XmlPullParser.START_TAG
        tagStack.add(tagName!!)
        depth++
        if (pos < data.length && data[pos] == '/') {
            consume('/')
            pendingEndTag = true
            emptyElement = true
        }
        consume('>')
    }

    private fun parseEndTag() {
        consume('<')
        consume('/')
        tagName = readName()
        skipWhitespace()
        consume('>')
        textValue = null
        event = XmlPullParser.END_TAG
        val expected = tagStack.lastOrNull()
        if (expected == null || expected != tagName) {
            throw XmlPullParserException("mismatched end tag $tagName")
        }
        popTag()
    }

    private fun popTag() {
        if (tagStack.isNotEmpty()) {
            tagStack.removeAt(tagStack.lastIndex)
        }
        if (depth > 0) {
            depth--
        }
    }

    private fun parseText() {
        val decoded = StringBuilder()
        while (pos < data.length && data[pos] != '<') {
            val ch = data[pos]
            if (ch == '&') {
                decoded.append(readEntity())
            } else {
                decoded.append(normalizeEol(ch))
                advance()
            }
        }
        textValue = decoded.toString()
        tagName = null
        event = XmlPullParser.TEXT
    }

    private fun parseCdata() {
        skipUntil("<![CDATA[")
        val start = pos
        val end = data.indexOf("]]>", start)
        if (end < 0) {
            throw XmlPullParserException("unterminated CDATA")
        }
        textValue = data.substring(start, end)
        pos = end + 3
        tagName = null
        event = XmlPullParser.TEXT
    }

    private fun skipDoctype() {
        skipUntil("<!DOCTYPE")
        var inQuote: Char? = null
        var bracket = 0
        while (pos < data.length) {
            val ch = data[pos]
            advance()
            when {
                inQuote != null -> if (ch == inQuote) inQuote = null
                ch == '"' || ch == '\'' -> inQuote = ch
                ch == '[' -> bracket++
                ch == ']' -> if (bracket > 0) bracket--
                ch == '>' && bracket == 0 -> return
            }
        }
        throw XmlPullParserException("unterminated DOCTYPE")
    }

    private fun skipUntil(token: String) {
        val at = data.indexOf(token, pos)
        if (at < 0) {
            throw XmlPullParserException("missing $token")
        }
        pos = at + token.length
    }

    private fun skipAttributes() {
        while (pos < data.length) {
            skipWhitespace()
            if (pos >= data.length) {
                throw XmlPullParserException("unterminated start tag")
            }
            val ch = data[pos]
            if (ch == '>' || ch == '/') {
                return
            }
            readName()
            skipWhitespace()
            consume('=')
            skipWhitespace()
            skipQuoted()
        }
    }

    private fun skipQuoted() {
        if (pos >= data.length) {
            throw XmlPullParserException("unterminated attribute")
        }
        val quote = data[pos]
        if (quote != '"' && quote != '\'') {
            throw XmlPullParserException("attribute is not quoted")
        }
        advance()
        while (pos < data.length && data[pos] != quote) {
            advance()
        }
        consume(quote)
    }

    private fun readName(): String {
        val start = pos
        while (pos < data.length) {
            val ch = data[pos]
            if (ch.isWhitespace() || ch == '/' || ch == '>' || ch == '=') {
                break
            }
            advance()
        }
        if (start == pos) {
            throw XmlPullParserException("expected name")
        }
        return data.substring(start, pos)
    }

    private fun readEntity(): String {
        consume('&')
        val start = pos
        while (pos < data.length && data[pos] != ';') {
            advance()
        }
        if (pos >= data.length) {
            throw XmlPullParserException("unterminated entity")
        }
        val body = data.substring(start, pos)
        consume(';')
        return when {
            body == "lt" -> "<"

            body == "gt" -> ">"

            body == "amp" -> "&"

            body == "quot" -> "\""

            body == "apos" -> "'"

            body.startsWith("#x") || body.startsWith("#X") -> {
                val cp = body.substring(2).toInt(16)
                String(Character.toChars(cp))
            }

            body.startsWith("#") -> {
                val cp = body.substring(1).toInt(10)
                String(Character.toChars(cp))
            }

            else -> throw XmlPullParserException("unknown entity &$body;")
        }
    }

    private fun skipWhitespace() {
        while (pos < data.length && data[pos].isWhitespace()) {
            advance()
        }
    }

    private fun consume(expected: Char) {
        if (pos >= data.length || data[pos] != expected) {
            throw XmlPullParserException("expected '$expected'")
        }
        advance()
    }

    private fun advance() {
        if (pos < data.length) {
            if (data[pos] == '\n') {
                line++
                column = 1
            } else {
                column++
            }
            pos++
        }
    }

    private fun normalizeEol(ch: Char): Char {
        if (ch == '\r') {
            if (pos + 1 < data.length && data[pos + 1] == '\n') {
                advance()
            }
            return '\n'
        }
        return ch
    }
}
