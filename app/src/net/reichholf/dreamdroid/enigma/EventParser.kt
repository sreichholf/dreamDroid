package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

object EventParser {
    fun parse(xml: String): List<Event> {
        if (xml.isEmpty()) {
            return emptyList()
        }
        return try {
            val handler = EventListHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(xml)))
            handler.events
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private class EventListHandler : DefaultHandler() {
    val events = ArrayList<Event>()
    private var inEvent = false
    private var inId = false
    private var inStart = false
    private var inDuration = false
    private var inCurrentTime = false
    private var inTitle = false
    private var inDescription = false
    private var inDescriptionEx = false
    private var inServiceRef = false
    private var inServiceName = false

    private val eventId = StringBuilder()
    private val title = StringBuilder()
    private val start = StringBuilder()
    private val duration = StringBuilder()
    private val currentTime = StringBuilder()
    private val description = StringBuilder()
    private val descriptionExtended = StringBuilder()
    private val serviceReference = StringBuilder()
    private val serviceName = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (tag(localName, qName)) {
            "e2event" -> {
                inEvent = true
                eventId.setLength(0)
                title.setLength(0)
                start.setLength(0)
                duration.setLength(0)
                currentTime.setLength(0)
                description.setLength(0)
                descriptionExtended.setLength(0)
                serviceReference.setLength(0)
                serviceName.setLength(0)
            }
            "e2eventid" -> inId = true
            "e2eventstart" -> inStart = true
            "e2eventduration" -> inDuration = true
            "e2eventcurrenttime" -> inCurrentTime = true
            "e2eventtitle" -> inTitle = true
            "e2eventdescription" -> inDescription = true
            "e2eventdescriptionextended" -> inDescriptionEx = true
            "e2eventservicereference" -> inServiceRef = true
            "e2eventservicename" -> inServiceName = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (tag(localName, qName)) {
            "e2event" -> {
                inEvent = false
                events.add(buildEvent())
            }
            "e2eventid" -> inId = false
            "e2eventstart" -> inStart = false
            "e2eventduration" -> inDuration = false
            "e2eventcurrenttime" -> inCurrentTime = false
            "e2eventtitle" -> inTitle = false
            "e2eventdescription" -> inDescription = false
            "e2eventdescriptionextended" -> inDescriptionEx = false
            "e2eventservicereference" -> inServiceRef = false
            "e2eventservicename" -> inServiceName = false
        }
    }

    override fun characters(ch: CharArray, startIdx: Int, length: Int) {
        if (!inEvent) {
            return
        }
        when {
            inId -> eventId.append(ch, startIdx, length)
            inStart -> start.append(ch, startIdx, length)
            inDuration -> duration.append(ch, startIdx, length)
            inCurrentTime -> currentTime.append(ch, startIdx, length)
            inTitle -> title.append(ch, startIdx, length)
            inDescription -> description.append(ch, startIdx, length)
            inDescriptionEx -> descriptionExtended.append(ch, startIdx, length)
            inServiceRef -> serviceReference.append(ch, startIdx, length)
            inServiceName -> serviceName.append(ch, startIdx, length)
        }
    }

    private fun buildEvent(): Event {
        val startRaw = start.toString().trim()
        val durationRaw = duration.toString().trim()
        var titleRaw = title.toString().trim()
        if (titleRaw.isEmpty() || Python.NONE == titleRaw) {
            titleRaw = "N/A"
        }
        val extDesc = descriptionExtended.toString().replace("\u008A", "\n")

        var startReadable = ""
        var startTimeReadable = ""
        var durationReadable = ""
        if (startRaw.isNotEmpty() && Python.NONE != startRaw) {
            startReadable = DateTime.getDateTimeString(startRaw)
            startTimeReadable = DateTime.getTimeString(startRaw)
            durationReadable = try {
                DateTime.getDurationString(durationRaw, startRaw) ?: durationRaw
            } catch (e: NumberFormatException) {
                durationRaw
            }
        }

        return Event(
            eventId = eventId.toString().trim(),
            title = titleRaw,
            start = startRaw,
            duration = durationRaw,
            currentTime = currentTime.toString().trim(),
            description = description.toString(),
            descriptionExtended = extDesc,
            serviceReference = serviceReference.toString().trim(),
            serviceName = serviceName.toString().replace("\\p{Cntrl}".toRegex(), "").trim(),
            startReadable = startReadable,
            startTimeReadable = startTimeReadable,
            durationReadable = durationReadable
        )
    }

    private fun tag(localName: String?, qName: String?): String {
        val raw = if (!localName.isNullOrEmpty()) localName else (qName ?: "")
        val colon = raw.lastIndexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }
}
