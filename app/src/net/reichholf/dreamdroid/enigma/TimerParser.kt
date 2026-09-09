package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

object TimerParser {
    /**
     * @return parsed timers, or null when XML cannot be parsed (distinct from a valid empty list).
     */
    fun parse(xml: String): List<Timer>? {
        if (xml.isEmpty()) {
            return null
        }
        return parseSanitized(xml, aggressive = false)
            ?: parseSanitized(xml, aggressive = true)
    }

    private fun parseSanitized(xml: String, aggressive: Boolean): List<Timer>? {
        return try {
            val handler = TimerListHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(XmlInput.sanitize(xml, aggressive))))
            handler.timers
        } catch (e: Exception) {
            null
        }
    }
}

private class TimerListHandler : DefaultHandler() {
    val timers = ArrayList<Timer>()

    private var inTimer = false
    private var inReference = false
    private var inServiceName = false
    private var inEit = false
    private var inName = false
    private var inDescription = false
    private var inDescriptionEx = false
    private var inDisabled = false
    private var inBegin = false
    private var inEnd = false
    private var inDuration = false
    private var inStartPrepare = false
    private var inJustPlay = false
    private var inAfterEvent = false
    private var inLocation = false
    private var inTags = false
    private var inLogEntries = false
    private var inFileName = false
    private var inBackOff = false
    private var inNextActivation = false
    private var inFirstTryPrepare = false
    private var inState = false
    private var inRepeated = false
    private var inDontSave = false
    private var inCanceled = false
    private var inToggleDisabled = false

    private val reference = StringBuilder()
    private val serviceName = StringBuilder()
    private val eit = StringBuilder()
    private val name = StringBuilder()
    private val description = StringBuilder()
    private val descriptionExtended = StringBuilder()
    private val disabled = StringBuilder()
    private val begin = StringBuilder()
    private val end = StringBuilder()
    private val duration = StringBuilder()
    private val startPrepare = StringBuilder()
    private val justPlay = StringBuilder()
    private val afterEvent = StringBuilder()
    private val location = StringBuilder()
    private val tags = StringBuilder()
    private val logEntries = StringBuilder()
    private val fileName = StringBuilder()
    private val backOff = StringBuilder()
    private val nextActivation = StringBuilder()
    private val firstTryPrepare = StringBuilder()
    private val state = StringBuilder()
    private val repeated = StringBuilder()
    private val dontSave = StringBuilder()
    private val canceled = StringBuilder()
    private val toggleDisabled = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (tag(localName, qName)) {
            "e2timer" -> {
                inTimer = true
                clearBuilders()
            }
            "e2servicereference" -> inReference = true
            "e2servicename" -> inServiceName = true
            "e2eit" -> inEit = true
            "e2name" -> inName = true
            "e2description" -> inDescription = true
            "e2descriptionextended" -> inDescriptionEx = true
            "e2disabled" -> inDisabled = true
            "e2timebegin" -> inBegin = true
            "e2timeend" -> inEnd = true
            "e2duration" -> inDuration = true
            "e2startprepare" -> inStartPrepare = true
            "e2justplay" -> inJustPlay = true
            "e2afterevent" -> inAfterEvent = true
            "e2location" -> inLocation = true
            "e2tags" -> inTags = true
            "e2logentries" -> inLogEntries = true
            "e2filename" -> inFileName = true
            "e2backoff" -> inBackOff = true
            "e2nextactivation" -> inNextActivation = true
            "e2firsttryprepare" -> inFirstTryPrepare = true
            "e2state" -> inState = true
            "e2repeated" -> inRepeated = true
            "e2dontsave" -> inDontSave = true
            "e2cancled" -> inCanceled = true
            "e2toggledisabled" -> inToggleDisabled = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (tag(localName, qName)) {
            "e2timer" -> {
                inTimer = false
                timers.add(buildTimer())
            }
            "e2servicereference" -> inReference = false
            "e2servicename" -> inServiceName = false
            "e2eit" -> inEit = false
            "e2name" -> inName = false
            "e2description" -> inDescription = false
            "e2descriptionextended" -> inDescriptionEx = false
            "e2disabled" -> inDisabled = false
            "e2timebegin" -> inBegin = false
            "e2timeend" -> inEnd = false
            "e2duration" -> inDuration = false
            "e2startprepare" -> inStartPrepare = false
            "e2justplay" -> inJustPlay = false
            "e2afterevent" -> inAfterEvent = false
            "e2location" -> inLocation = false
            "e2tags" -> inTags = false
            "e2logentries" -> inLogEntries = false
            "e2filename" -> inFileName = false
            "e2backoff" -> inBackOff = false
            "e2nextactivation" -> inNextActivation = false
            "e2firsttryprepare" -> inFirstTryPrepare = false
            "e2state" -> inState = false
            "e2repeated" -> inRepeated = false
            "e2dontsave" -> inDontSave = false
            "e2cancled" -> inCanceled = false
            "e2toggledisabled" -> inToggleDisabled = false
        }
    }

    override fun characters(ch: CharArray, startIdx: Int, length: Int) {
        if (!inTimer) {
            return
        }
        when {
            inReference -> reference.append(ch, startIdx, length)
            inServiceName -> serviceName.append(ch, startIdx, length)
            inEit -> eit.append(ch, startIdx, length)
            inName -> name.append(ch, startIdx, length)
            inDescription -> description.append(ch, startIdx, length)
            inDescriptionEx -> descriptionExtended.append(ch, startIdx, length)
            inDisabled -> disabled.append(ch, startIdx, length)
            inBegin -> begin.append(ch, startIdx, length)
            inEnd -> end.append(ch, startIdx, length)
            inDuration -> duration.append(ch, startIdx, length)
            inStartPrepare -> startPrepare.append(ch, startIdx, length)
            inJustPlay -> justPlay.append(ch, startIdx, length)
            inAfterEvent -> afterEvent.append(ch, startIdx, length)
            inLocation -> location.append(ch, startIdx, length)
            inTags -> tags.append(ch, startIdx, length)
            inLogEntries -> logEntries.append(ch, startIdx, length)
            inFileName -> fileName.append(ch, startIdx, length)
            inBackOff -> backOff.append(ch, startIdx, length)
            inNextActivation -> nextActivation.append(ch, startIdx, length)
            inFirstTryPrepare -> firstTryPrepare.append(ch, startIdx, length)
            inState -> state.append(ch, startIdx, length)
            inRepeated -> repeated.append(ch, startIdx, length)
            inDontSave -> dontSave.append(ch, startIdx, length)
            inCanceled -> canceled.append(ch, startIdx, length)
            inToggleDisabled -> toggleDisabled.append(ch, startIdx, length)
        }
    }

    private fun clearBuilders() {
        reference.setLength(0)
        serviceName.setLength(0)
        eit.setLength(0)
        name.setLength(0)
        description.setLength(0)
        descriptionExtended.setLength(0)
        disabled.setLength(0)
        begin.setLength(0)
        end.setLength(0)
        duration.setLength(0)
        startPrepare.setLength(0)
        justPlay.setLength(0)
        afterEvent.setLength(0)
        location.setLength(0)
        tags.setLength(0)
        logEntries.setLength(0)
        fileName.setLength(0)
        backOff.setLength(0)
        nextActivation.setLength(0)
        firstTryPrepare.setLength(0)
        state.setLength(0)
        repeated.setLength(0)
        dontSave.setLength(0)
        canceled.setLength(0)
        toggleDisabled.setLength(0)
    }

    private fun buildTimer(): Timer {
        val beginRaw = begin.toString().trim()
        val endRaw = end.toString().trim()
        val durationRaw = duration.toString().trim()

        var beginReadable = ""
        var endReadable = ""
        var durationReadable = ""
        if (beginRaw.isNotEmpty() && Python.NONE != beginRaw) {
            beginReadable = DateTime.getYearDateTimeString(beginRaw)
        }
        if (endRaw.isNotEmpty() && Python.NONE != endRaw) {
            endReadable = DateTime.getYearDateTimeString(endRaw)
        }
        if (durationRaw.isNotEmpty() && Python.NONE != durationRaw) {
            durationReadable = try {
                DateTime.getDurationString(durationRaw, null) ?: durationRaw
            } catch (e: NumberFormatException) {
                durationRaw
            }
        }

        return Timer(
            reference = reference.toString().trim(),
            serviceName = serviceName.toString().replace("\\p{Cntrl}".toRegex(), "").trim(),
            eit = eit.toString().trim(),
            name = name.toString().trim(),
            description = description.toString(),
            descriptionExtended = descriptionExtended.toString(),
            disabled = disabled.toString().trim(),
            begin = beginRaw,
            end = endRaw,
            duration = durationRaw,
            beginReadable = beginReadable,
            endReadable = endReadable,
            durationReadable = durationReadable,
            startPrepare = startPrepare.toString().trim(),
            justPlay = justPlay.toString().trim(),
            afterEvent = afterEvent.toString().trim(),
            location = location.toString().trim(),
            tags = tags.toString().trim(),
            logEntries = logEntries.toString(),
            fileName = fileName.toString().trim(),
            backOff = backOff.toString().trim(),
            nextActivation = nextActivation.toString().trim(),
            firstTryPrepare = firstTryPrepare.toString().trim(),
            state = state.toString().trim(),
            repeated = repeated.toString().trim(),
            dontSave = dontSave.toString().trim(),
            canceled = canceled.toString().trim(),
            toggleDisabled = toggleDisabled.toString().trim(),
        )
    }

    private fun tag(localName: String?, qName: String?): String {
        val raw = if (!localName.isNullOrEmpty()) localName else (qName ?: "")
        val colon = raw.lastIndexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }
}
