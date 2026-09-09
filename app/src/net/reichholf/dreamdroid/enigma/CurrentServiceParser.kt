package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

object CurrentServiceParser {
    fun parse(xml: String): CurrentService? {
        if (xml.isEmpty()) {
            return null
        }
        return parseSanitized(xml, aggressive = false)
            ?: parseSanitized(xml, aggressive = true)
    }

    private fun parseSanitized(xml: String, aggressive: Boolean): CurrentService? {
        return try {
            val handler = CurrentServiceHandler()
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

private class CurrentServiceHandler : DefaultHandler() {
    var result: CurrentService? = null

    private var inService = false
    private var inEvent = false

    private var inServiceReference = false
    private var inServiceName = false
    private var inProviderName = false

    private var inEventServiceReference = false
    private var inEventServiceName = false
    private var inEventId = false
    private var inEventTitle = false
    private var inEventName = false
    private var inEventDescription = false
    private var inEventStart = false
    private var inEventDuration = false
    private var inEventCurrentTime = false
    private var inEventDescriptionExtended = false

    private val serviceReference = StringBuilder()
    private val serviceName = StringBuilder()
    private val provider = StringBuilder()

    private val eventId = StringBuilder()
    private val title = StringBuilder()
    private val eventName = StringBuilder()
    private val start = StringBuilder()
    private val duration = StringBuilder()
    private val currentTime = StringBuilder()
    private val description = StringBuilder()
    private val descriptionExtended = StringBuilder()
    private val eventServiceReference = StringBuilder()
    private val eventServiceName = StringBuilder()

    private var service: Service = Service("", "")
    private val events = ArrayList<Event>()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (tag(localName, qName)) {
            "e2service" -> {
                inService = true
                serviceReference.setLength(0)
                serviceName.setLength(0)
                provider.setLength(0)
            }
            "e2servicereference" -> if (inService) inServiceReference = true
            "e2servicename" -> if (inService) inServiceName = true
            "e2providername" -> if (inService) inProviderName = true
            "e2event" -> {
                inEvent = true
                eventId.setLength(0)
                title.setLength(0)
                eventName.setLength(0)
                start.setLength(0)
                duration.setLength(0)
                currentTime.setLength(0)
                description.setLength(0)
                descriptionExtended.setLength(0)
                eventServiceReference.setLength(0)
                eventServiceName.setLength(0)
            }
            "e2eventservicereference" -> if (inEvent) inEventServiceReference = true
            "e2eventservicename" -> if (inEvent) inEventServiceName = true
            "e2eventid" -> if (inEvent) inEventId = true
            "e2eventtitle" -> if (inEvent) inEventTitle = true
            "e2eventname" -> if (inEvent) inEventName = true
            "e2eventdescription" -> if (inEvent) inEventDescription = true
            "e2eventstart" -> if (inEvent) inEventStart = true
            "e2eventduration" -> if (inEvent) inEventDuration = true
            "e2eventcurrenttime" -> if (inEvent) inEventCurrentTime = true
            "e2eventdescriptionextended" -> if (inEvent) inEventDescriptionExtended = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (tag(localName, qName)) {
            "e2service" -> {
                inService = false
                service = Service(
                    reference = serviceReference.toString().trim(),
                    name = serviceName.toString().replace("\\p{Cntrl}".toRegex(), "").trim(),
                    provider = provider.toString().trim()
                )
            }
            "e2servicereference" -> inServiceReference = false
            "e2servicename" -> inServiceName = false
            "e2providername" -> inProviderName = false
            "e2event" -> {
                inEvent = false
                events.add(buildEvent())
            }
            "e2eventservicereference" -> inEventServiceReference = false
            "e2eventservicename" -> inEventServiceName = false
            "e2eventid" -> inEventId = false
            "e2eventtitle" -> inEventTitle = false
            "e2eventname" -> inEventName = false
            "e2eventdescription" -> inEventDescription = false
            "e2eventstart" -> inEventStart = false
            "e2eventduration" -> inEventDuration = false
            "e2eventcurrenttime" -> inEventCurrentTime = false
            "e2eventdescriptionextended" -> inEventDescriptionExtended = false
            "e2currentserviceinformation" -> {
                result = CurrentService(
                    service = service,
                    now = events.getOrNull(0),
                    next = events.getOrNull(1)
                )
            }
        }
    }

    override fun characters(ch: CharArray, startIdx: Int, length: Int) {
        if (inService) {
            when {
                inServiceReference -> serviceReference.append(ch, startIdx, length)
                inServiceName -> serviceName.append(ch, startIdx, length)
                inProviderName -> provider.append(ch, startIdx, length)
            }
            return
        }
        if (!inEvent) {
            return
        }
        when {
            inEventServiceReference -> eventServiceReference.append(ch, startIdx, length)
            inEventServiceName -> eventServiceName.append(ch, startIdx, length)
            inEventId -> eventId.append(ch, startIdx, length)
            inEventTitle -> title.append(ch, startIdx, length)
            inEventName -> eventName.append(ch, startIdx, length)
            inEventDescription -> description.append(ch, startIdx, length)
            inEventStart -> start.append(ch, startIdx, length)
            inEventDuration -> duration.append(ch, startIdx, length)
            inEventCurrentTime -> currentTime.append(ch, startIdx, length)
            inEventDescriptionExtended -> descriptionExtended.append(ch, startIdx, length)
        }
    }

    private fun buildEvent(): Event {
        val startRaw = start.toString().trim()
        val durationRaw = duration.toString().trim()
        var titleRaw = title.toString().trim()
        if (titleRaw.isEmpty() || Python.NONE == titleRaw) {
            // WebInterface 1.5 may send e2eventname instead of e2eventtitle.
            titleRaw = eventName.toString().trim()
        }
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
            serviceReference = eventServiceReference.toString().trim(),
            serviceName = eventServiceName.toString().replace("\\p{Cntrl}".toRegex(), "").trim(),
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
