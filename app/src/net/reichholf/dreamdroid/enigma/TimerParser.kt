package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import org.xmlpull.v1.XmlPullParser

object TimerParser {
    /**
     * @return parsed timers, or null when XML cannot be parsed (distinct from a valid empty list).
     */
    fun parse(xml: String): List<Timer>? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseTimerList(parser)
        }
}

private fun parseTimerList(parser: XmlPullParser): List<Timer> {
    val timers = ArrayList<Timer>()
    val reference = StringBuilder()
    val serviceName = StringBuilder()
    val eit = StringBuilder()
    val name = StringBuilder()
    val description = StringBuilder()
    val descriptionExtended = StringBuilder()
    val disabled = StringBuilder()
    val begin = StringBuilder()
    val end = StringBuilder()
    val duration = StringBuilder()
    val startPrepare = StringBuilder()
    val justPlay = StringBuilder()
    val afterEvent = StringBuilder()
    val location = StringBuilder()
    val tags = StringBuilder()
    val logEntries = StringBuilder()
    val fileName = StringBuilder()
    val backOff = StringBuilder()
    val nextActivation = StringBuilder()
    val firstTryPrepare = StringBuilder()
    val state = StringBuilder()
    val repeated = StringBuilder()
    val dontSave = StringBuilder()
    val canceled = StringBuilder()
    val toggleDisabled = StringBuilder()
    var current: StringBuilder? = null
    var inTimer = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2timer" -> {
                        inTimer = true
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
                        current = null
                    }

                    "e2servicereference" -> if (inTimer) current = reference

                    "e2servicename" -> if (inTimer) current = serviceName

                    "e2eit" -> if (inTimer) current = eit

                    "e2name" -> if (inTimer) current = name

                    "e2description" -> if (inTimer) current = description

                    "e2descriptionextended" -> if (inTimer) current = descriptionExtended

                    "e2disabled" -> if (inTimer) current = disabled

                    "e2timebegin" -> if (inTimer) current = begin

                    "e2timeend" -> if (inTimer) current = end

                    "e2duration" -> if (inTimer) current = duration

                    "e2startprepare" -> if (inTimer) current = startPrepare

                    "e2justplay" -> if (inTimer) current = justPlay

                    "e2afterevent" -> if (inTimer) current = afterEvent

                    "e2location" -> if (inTimer) current = location

                    "e2tags" -> if (inTimer) current = tags

                    "e2logentries" -> if (inTimer) current = logEntries

                    "e2filename" -> if (inTimer) current = fileName

                    "e2backoff" -> if (inTimer) current = backOff

                    "e2nextactivation" -> if (inTimer) current = nextActivation

                    "e2firsttryprepare" -> if (inTimer) current = firstTryPrepare

                    "e2state" -> if (inTimer) current = state

                    "e2repeated" -> if (inTimer) current = repeated

                    "e2dontsave" -> if (inTimer) current = dontSave

                    "e2cancled" -> if (inTimer) current = canceled

                    "e2toggledisabled" -> if (inTimer) current = toggleDisabled
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                when (parser.localTag()) {
                    "e2timer" -> {
                        inTimer = false
                        current = null
                        timers.add(
                            buildTimer(
                                reference = reference.toString().trim(),
                                serviceName = serviceName.toString(),
                                eit = eit.toString().trim(),
                                name = name.toString().trim(),
                                description = description.toString(),
                                descriptionExtended = descriptionExtended.toString(),
                                disabled = disabled.toString().trim(),
                                beginRaw = begin.toString().trim(),
                                endRaw = end.toString().trim(),
                                durationRaw = duration.toString().trim(),
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
                                toggleDisabled = toggleDisabled.toString().trim()
                            )
                        )
                    }

                    else -> current = null
                }
            }
        }
        event = parser.next()
    }
    return timers
}

private fun buildTimer(
    reference: String,
    serviceName: String,
    eit: String,
    name: String,
    description: String,
    descriptionExtended: String,
    disabled: String,
    beginRaw: String,
    endRaw: String,
    durationRaw: String,
    startPrepare: String,
    justPlay: String,
    afterEvent: String,
    location: String,
    tags: String,
    logEntries: String,
    fileName: String,
    backOff: String,
    nextActivation: String,
    firstTryPrepare: String,
    state: String,
    repeated: String,
    dontSave: String,
    canceled: String,
    toggleDisabled: String
): Timer {
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
        reference = reference,
        serviceName = serviceName.stripCntrl().trim(),
        eit = eit,
        name = name,
        description = description,
        descriptionExtended = descriptionExtended,
        disabled = disabled,
        begin = beginRaw,
        end = endRaw,
        duration = durationRaw,
        beginReadable = beginReadable,
        endReadable = endReadable,
        durationReadable = durationReadable,
        startPrepare = startPrepare,
        justPlay = justPlay,
        afterEvent = afterEvent,
        location = location,
        tags = tags,
        logEntries = logEntries,
        fileName = fileName,
        backOff = backOff,
        nextActivation = nextActivation,
        firstTryPrepare = firstTryPrepare,
        state = state,
        repeated = repeated,
        dontSave = dontSave,
        canceled = canceled,
        toggleDisabled = toggleDisabled
    )
}
