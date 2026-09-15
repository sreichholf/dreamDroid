package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import org.xmlpull.v1.XmlPullParser

object EventParser {
    fun parse(xml: String): List<Event> =
        parseEnigmaXml(xml, emptyResult = emptyList(), onFail = emptyList()) { parser ->
            parseEventList(parser)
        }
}

private fun parseEventList(parser: XmlPullParser): List<Event> {
    val events = ArrayList<Event>()
    val eventId = StringBuilder()
    val title = StringBuilder()
    val start = StringBuilder()
    val duration = StringBuilder()
    val currentTime = StringBuilder()
    val description = StringBuilder()
    val descriptionExtended = StringBuilder()
    val serviceReference = StringBuilder()
    val serviceName = StringBuilder()
    var current: StringBuilder? = null
    var inEvent = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
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
                        current = null
                    }

                    "e2eventid" -> if (inEvent) current = eventId

                    "e2eventstart" -> if (inEvent) current = start

                    "e2eventduration" -> if (inEvent) current = duration

                    "e2eventcurrenttime" -> if (inEvent) current = currentTime

                    "e2eventtitle" -> if (inEvent) current = title

                    "e2eventdescription" -> if (inEvent) current = description

                    "e2eventdescriptionextended" -> if (inEvent) current = descriptionExtended

                    "e2eventservicereference" -> if (inEvent) current = serviceReference

                    "e2eventservicename" -> if (inEvent) current = serviceName
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                when (parser.localTag()) {
                    "e2event" -> {
                        inEvent = false
                        current = null
                        events.add(
                            buildEvent(
                                eventId = eventId.toString().trim(),
                                titleRaw = title.toString().trim(),
                                eventNameRaw = "",
                                startRaw = start.toString().trim(),
                                durationRaw = duration.toString().trim(),
                                currentTime = currentTime.toString().trim(),
                                description = description.toString(),
                                descriptionExtended = descriptionExtended.toString(),
                                serviceReference = serviceReference.toString().trim(),
                                serviceName = serviceName.toString()
                            )
                        )
                    }

                    else -> current = null
                }
            }
        }
        event = parser.next()
    }
    return events
}

internal fun buildEvent(
    eventId: String,
    titleRaw: String,
    eventNameRaw: String,
    startRaw: String,
    durationRaw: String,
    currentTime: String,
    description: String,
    descriptionExtended: String,
    serviceReference: String,
    serviceName: String,
    useEventNameFallback: Boolean = false
): Event {
    var title = titleRaw
    if (title.isEmpty() || Python.NONE == title) {
        if (useEventNameFallback) {
            // WebInterface 1.5 may send e2eventname instead of e2eventtitle.
            title = eventNameRaw.trim()
        }
    }
    if (title.isEmpty() || Python.NONE == title) {
        title = "N/A"
    }
    val extDesc = descriptionExtended.replace("\u008A", "\n")

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
        eventId = eventId,
        title = title,
        start = startRaw,
        duration = durationRaw,
        currentTime = currentTime,
        description = description,
        descriptionExtended = extDesc,
        serviceReference = serviceReference,
        serviceName = serviceName.stripCntrl().trim(),
        startReadable = startReadable,
        startTimeReadable = startTimeReadable,
        durationReadable = durationReadable
    )
}
