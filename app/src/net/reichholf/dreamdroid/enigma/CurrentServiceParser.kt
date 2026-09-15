package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object CurrentServiceParser {
    fun parse(xml: String): CurrentService? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseCurrentService(parser)
        }
}

private fun parseCurrentService(parser: XmlPullParser): CurrentService? {
    val serviceReference = StringBuilder()
    val serviceName = StringBuilder()
    val provider = StringBuilder()

    val eventId = StringBuilder()
    val title = StringBuilder()
    val eventName = StringBuilder()
    val start = StringBuilder()
    val duration = StringBuilder()
    val currentTime = StringBuilder()
    val description = StringBuilder()
    val descriptionExtended = StringBuilder()
    val eventServiceReference = StringBuilder()
    val eventServiceName = StringBuilder()

    var service = Service("", "")
    val events = ArrayList<Event>()
    var result: CurrentService? = null
    var current: StringBuilder? = null
    var inService = false
    var inEvent = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2service" -> {
                        inService = true
                        serviceReference.setLength(0)
                        serviceName.setLength(0)
                        provider.setLength(0)
                        current = null
                    }

                    "e2servicereference" -> if (inService) current = serviceReference

                    "e2servicename" -> if (inService) current = serviceName

                    "e2providername" -> if (inService) current = provider

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
                        current = null
                    }

                    "e2eventservicereference" ->
                        if (inEvent) current = eventServiceReference

                    "e2eventservicename" -> if (inEvent) current = eventServiceName

                    "e2eventid" -> if (inEvent) current = eventId

                    "e2eventtitle" -> if (inEvent) current = title

                    "e2eventname" -> if (inEvent) current = eventName

                    "e2eventdescription" -> if (inEvent) current = description

                    "e2eventstart" -> if (inEvent) current = start

                    "e2eventduration" -> if (inEvent) current = duration

                    "e2eventcurrenttime" -> if (inEvent) current = currentTime

                    "e2eventdescriptionextended" ->
                        if (inEvent) current = descriptionExtended
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                when (parser.localTag()) {
                    "e2service" -> {
                        inService = false
                        current = null
                        service = Service(
                            reference = serviceReference.toString().trim(),
                            name = serviceName.toString().stripCntrl().trim(),
                            provider = provider.toString().trim()
                        )
                    }

                    "e2event" -> {
                        inEvent = false
                        current = null
                        events.add(
                            buildEvent(
                                eventId = eventId.toString().trim(),
                                titleRaw = title.toString().trim(),
                                eventNameRaw = eventName.toString(),
                                startRaw = start.toString().trim(),
                                durationRaw = duration.toString().trim(),
                                currentTime = currentTime.toString().trim(),
                                description = description.toString(),
                                descriptionExtended = descriptionExtended.toString(),
                                serviceReference = eventServiceReference.toString().trim(),
                                serviceName = eventServiceName.toString(),
                                useEventNameFallback = true
                            )
                        )
                    }

                    "e2currentserviceinformation" -> {
                        current = null
                        result = CurrentService(
                            service = service,
                            now = events.getOrNull(0),
                            next = events.getOrNull(1)
                        )
                    }

                    else -> current = null
                }
            }
        }
        event = parser.next()
    }
    return result
}
