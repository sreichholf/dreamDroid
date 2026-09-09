package net.reichholf.dreamdroid.enigma

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

object SignalParser {
    fun parse(xml: String): Signal? {
        if (xml.isEmpty()) {
            return null
        }
        return parseSanitized(xml, aggressive = false)
            ?: parseSanitized(xml, aggressive = true)
    }

    private fun parseSanitized(xml: String, aggressive: Boolean): Signal? {
        return try {
            val handler = SignalHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(XmlInput.sanitize(xml, aggressive))))
            handler.result
        } catch (e: Exception) {
            null
        }
    }
}

private class SignalHandler : DefaultHandler() {
    var result: Signal? = null

    private var inSnrDb = false
    private var inSnr = false
    private var inBer = false
    private var inAgc = false

    private val snrDb = StringBuilder()
    private val snr = StringBuilder()
    private val ber = StringBuilder()
    private val agc = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (tag(localName, qName)) {
            "e2snrdb" -> inSnrDb = true
            "e2snr" -> inSnr = true
            "e2ber" -> inBer = true
            // OpenWebif historically emits the typo "e2acg"; accept both.
            "e2acg", "e2agc" -> inAgc = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (tag(localName, qName)) {
            "e2snrdb" -> inSnrDb = false
            "e2snr" -> inSnr = false
            "e2ber" -> inBer = false
            "e2acg", "e2agc" -> inAgc = false
            "e2frontendstatus" -> finalizeResult()
        }
    }

    override fun endDocument() {
        if (result == null) {
            finalizeResult()
        }
    }

    private fun finalizeResult() {
        val built = Signal(
            snrDbRaw = snrDb.toString().trim(),
            snrRaw = snr.toString().trim(),
            berRaw = ber.toString().trim(),
            agcRaw = agc.toString().trim(),
        )
        result = if (built.isEmpty()) null else built
    }

    override fun characters(ch: CharArray, startIdx: Int, length: Int) {
        when {
            inSnrDb -> snrDb.append(ch, startIdx, length)
            inSnr -> snr.append(ch, startIdx, length)
            inBer -> ber.append(ch, startIdx, length)
            inAgc -> agc.append(ch, startIdx, length)
        }
    }

    private fun tag(localName: String?, qName: String?): String {
        val raw = if (!localName.isNullOrEmpty()) localName else (qName ?: "")
        val colon = raw.lastIndexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }
}
