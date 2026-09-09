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
    private var inVideoWidth = false
    private var inVideoHeight = false
    private var inVideoSize = false
    private var inIsWideScreen = false
    private var inApid = false
    private var inVpid = false
    private var inPcrPid = false
    private var inPmtPid = false
    private var inTxtPid = false
    private var inTsid = false
    private var inOnid = false
    private var inSid = false

    private var inEventServiceReference = false
    private var inEventServiceName = false
    private var inEventId = false
    private var inEventTitle = false
    private var inEventDescription = false
    private var inEventStart = false
    private var inEventDuration = false
    private var inEventCurrentTime = false
    private var inEventDescriptionExtended = false

    private val serviceReference = StringBuilder()
    private val serviceName = StringBuilder()
    private val provider = StringBuilder()
    private val videoWidth = StringBuilder()
    private val videoHeight = StringBuilder()
    private val videoSize = StringBuilder()
    private val widescreen = StringBuilder()
    private val apid = StringBuilder()
    private val vpid = StringBuilder()
    private val pcrPid = StringBuilder()
    private val pmtPid = StringBuilder()
    private val txtPid = StringBuilder()
    private val tsid = StringBuilder()
    private val onid = StringBuilder()
    private val sid = StringBuilder()

    private val eventId = StringBuilder()
    private val title = StringBuilder()
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
                videoWidth.setLength(0)
                videoHeight.setLength(0)
                videoSize.setLength(0)
                widescreen.setLength(0)
                apid.setLength(0)
                vpid.setLength(0)
                pcrPid.setLength(0)
                pmtPid.setLength(0)
                txtPid.setLength(0)
                tsid.setLength(0)
                onid.setLength(0)
                sid.setLength(0)
            }
            "e2servicereference" -> if (inService) inServiceReference = true
            "e2servicename" -> if (inService) inServiceName = true
            "e2providername" -> if (inService) inProviderName = true
            "e2videowidth" -> if (inService) inVideoWidth = true
            "e2videoheight" -> if (inService) inVideoHeight = true
            "e2servicevideosize" -> if (inService) inVideoSize = true
            "e2iswidescreen" -> if (inService) inIsWideScreen = true
            "e2apid" -> if (inService) inApid = true
            "e2vpid" -> if (inService) inVpid = true
            "e2pcrpid" -> if (inService) inPcrPid = true
            "e2pmtpid" -> if (inService) inPmtPid = true
            "e2txtpid" -> if (inService) inTxtPid = true
            "e2tsid" -> if (inService) inTsid = true
            "e2onid" -> if (inService) inOnid = true
            "e2sid" -> if (inService) inSid = true
            "e2event" -> {
                inEvent = true
                eventId.setLength(0)
                title.setLength(0)
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
                    provider = provider.toString().trim(),
                    videoWidth = videoWidth.toString().trim(),
                    videoHeight = videoHeight.toString().trim(),
                    videoSize = videoSize.toString().trim(),
                    widescreen = widescreen.toString().trim(),
                    apid = apid.toString().trim(),
                    vpid = vpid.toString().trim(),
                    pcrPid = pcrPid.toString().trim(),
                    pmtPid = pmtPid.toString().trim(),
                    txtPid = txtPid.toString().trim(),
                    tsid = tsid.toString().trim(),
                    onid = onid.toString().trim(),
                    sid = sid.toString().trim()
                )
            }
            "e2servicereference" -> inServiceReference = false
            "e2servicename" -> inServiceName = false
            "e2providername" -> inProviderName = false
            "e2videowidth" -> inVideoWidth = false
            "e2videoheight" -> inVideoHeight = false
            "e2servicevideosize" -> inVideoSize = false
            "e2iswidescreen" -> inIsWideScreen = false
            "e2apid" -> inApid = false
            "e2vpid" -> inVpid = false
            "e2pcrpid" -> inPcrPid = false
            "e2pmtpid" -> inPmtPid = false
            "e2txtpid" -> inTxtPid = false
            "e2tsid" -> inTsid = false
            "e2onid" -> inOnid = false
            "e2sid" -> inSid = false
            "e2event" -> {
                inEvent = false
                events.add(buildEvent())
            }
            "e2eventservicereference" -> inEventServiceReference = false
            "e2eventservicename" -> inEventServiceName = false
            "e2eventid" -> inEventId = false
            "e2eventtitle" -> inEventTitle = false
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
                inVideoWidth -> videoWidth.append(ch, startIdx, length)
                inVideoHeight -> videoHeight.append(ch, startIdx, length)
                inVideoSize -> videoSize.append(ch, startIdx, length)
                inIsWideScreen -> widescreen.append(ch, startIdx, length)
                inApid -> apid.append(ch, startIdx, length)
                inVpid -> vpid.append(ch, startIdx, length)
                inPcrPid -> pcrPid.append(ch, startIdx, length)
                inPmtPid -> pmtPid.append(ch, startIdx, length)
                inTxtPid -> txtPid.append(ch, startIdx, length)
                inTsid -> tsid.append(ch, startIdx, length)
                inOnid -> onid.append(ch, startIdx, length)
                inSid -> sid.append(ch, startIdx, length)
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
