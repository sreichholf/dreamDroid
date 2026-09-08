package net.reichholf.dreamdroid.enigma

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

object ServiceParser {
    fun parse(xml: String): List<Service> {
        if (xml.isEmpty()) {
            return emptyList()
        }
        return try {
            val handler = ServiceListHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(xml)))
            handler.services
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private class ServiceListHandler : DefaultHandler() {
    val services = ArrayList<Service>()
    private var inService = false
    private var inReference = false
    private var inName = false
    private val reference = StringBuilder()
    private val name = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (tag(localName, qName)) {
            "e2service" -> {
                inService = true
                reference.setLength(0)
                name.setLength(0)
            }
            "e2servicereference" -> inReference = true
            "e2servicename" -> inName = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (tag(localName, qName)) {
            "e2service" -> {
                inService = false
                services.add(Service(reference.toString(), name.toString().replace("\\p{Cntrl}".toRegex(), "")))
            }
            "e2servicereference" -> inReference = false
            "e2servicename" -> inName = false
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (!inService) {
            return
        }
        if (inReference) {
            reference.append(ch, start, length)
        } else if (inName) {
            name.append(ch, start, length)
        }
    }

    private fun tag(localName: String?, qName: String?): String {
        val raw = if (!localName.isNullOrEmpty()) localName else (qName ?: "")
        val colon = raw.lastIndexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }
}
