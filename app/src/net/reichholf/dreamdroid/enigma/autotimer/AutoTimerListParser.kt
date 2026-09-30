package net.reichholf.dreamdroid.enigma.autotimer

import java.time.Instant
import java.time.LocalTime
import net.reichholf.dreamdroid.enigma.appendText
import net.reichholf.dreamdroid.enigma.localTag
import net.reichholf.dreamdroid.enigma.parseEnigmaXml
import org.xmlpull.v1.XmlPullParser

/**
 * `/autotimer` (`webif=true`, the default): the plugin's `<autotimer>` config with an `id`
 * per `<timer>` and channels and bouquets in one `<e2service>` list. `<defaults>` is skipped.
 * Null when the reply is not such a list.
 */
object AutoTimerListParser {
    fun parse(xml: String): List<AutoTimerEntry>? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser -> parseList(parser) }
}

private class RawTimer(val attributes: Map<String, String>) {
    val services = ArrayList<Pair<String, String>>()
    val afterEvents = ArrayList<Pair<Map<String, String>, String>>()
    val includes = ArrayList<Pair<String, String>>()
    val excludes = ArrayList<Pair<String, String>>()
    val tags = ArrayList<String>()
}

private fun parseList(parser: XmlPullParser): List<AutoTimerEntry>? {
    var sawRoot = false
    val entries = ArrayList<AutoTimerEntry>()
    var timer: RawTimer? = null
    var serviceRef = StringBuilder()
    var serviceName = StringBuilder()
    var elementAttributes: Map<String, String> = emptyMap()
    val text = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                text.setLength(0)
                when (parser.localTag()) {
                    "autotimer" -> sawRoot = true

                    "timer" -> timer = RawTimer(parser.attributeMap())

                    "e2service" -> {
                        serviceRef = StringBuilder()
                        serviceName = StringBuilder()
                    }

                    "afterevent", "include", "exclude" ->
                        elementAttributes = parser.attributeMap()
                }
            }

            XmlPullParser.TEXT -> parser.appendText(text)

            XmlPullParser.END_TAG -> {
                val current = timer
                if (current != null) {
                    when (parser.localTag()) {
                        "e2servicereference" -> serviceRef.append(text.trim())

                        "e2servicename" -> serviceName.append(text.trim())

                        "e2service" ->
                            current.services += serviceRef.toString() to serviceName.toString()

                        "afterevent" ->
                            current.afterEvents += elementAttributes to
                                text.toString().trim()

                        "include" ->
                            current.includes +=
                                elementAttributes["where"].orEmpty() to text.toString()

                        "exclude" ->
                            current.excludes +=
                                elementAttributes["where"].orEmpty() to text.toString()

                        "e2tags" -> current.tags += text.split(' ').filter { it.isNotBlank() }

                        "tag" -> current.tags += text.toString().trim()

                        "timer" -> {
                            current.toEntry()?.let { entries += it }
                            timer = null
                        }
                    }
                }
                text.setLength(0)
            }
        }
        event = parser.next()
    }
    return entries.takeIf { sawRoot }
}

private fun XmlPullParser.attributeMap(): Map<String, String> =
    (0 until attributeCount).associate { getAttributeName(it) to getAttributeValue(it) }

/** Null without a usable id: nothing could address that AutoTimer. */
private fun RawTimer.toEntry(): AutoTimerEntry? {
    val id = attributes["id"]?.toIntOrNull()?.let(::AutoTimerId) ?: return null
    val name = attributes["name"].orEmpty()
    return try {
        AutoTimerEntry.Readable(AutoTimer(id, settings(), extras()))
    } catch (e: UnreadableValue) {
        AutoTimerEntry.Unreadable(id, name, e.message.orEmpty())
    }
}

private class UnreadableValue(message: String) : Exception(message)

private fun unreadable(what: String, value: String?): Nothing =
    throw UnreadableValue("$what: $value")

private fun RawTimer.settings(): AutoTimerSettings {
    val a = attributes
    return AutoTimerSettings(
        name = a["name"].orEmpty(),
        match = a["match"].orEmpty(),
        enabled = a["enabled"] != "no",
        searchType = a["searchType"]?.let { token ->
            SearchType.entries.firstOrNull { it.token == token } ?: unreadable("searchType", token)
        } ?: SearchType.Partial,
        caseSensitive = a["searchCase"] == "sensitive",
        targets = services.map { (ref, name) -> Target.of(ref, name) },
        timeWindow = window(a["from"], a["to"], "from/to"),
        dateWindow = dateWindow(a["after"], a["before"]),
        offset = a["offset"]?.let(::offset),
        maxDurationMinutes = a["maxduration"]?.let { int("maxduration", it) },
        location = a["location"]?.takeIf { it.isNotEmpty() },
        tags = tags,
        include = filters(includes),
        exclude = filters(excludes),
        afterEvent = afterEvent(),
        recordMode = if (a["justplay"]?.let { int("justplay", it) } == 1) {
            RecordMode.Zap(setEndTime = a["setEndtime"] != "0")
        } else {
            RecordMode.Record
        },
        duplicates = duplicates(a["avoidDuplicateDescription"], a["searchForDuplicateDescription"])
    )
}

private fun RawTimer.extras(): Extras = Extras(
    counter = attributes["counter"]?.let { it != "0" } ?: false,
    vps = attributes["vps_enabled"] == "yes",
    seriesPlugin = attributes["series_labeling"] == "yes",
    overrideAlternatives = attributes["overrideAlternatives"]?.let { it != "0" } ?: false
)

private fun int(what: String, value: String): Int = value.toIntOrNull() ?: unreadable(what, value)

private fun clock(value: String): LocalTime {
    val parts = value.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull()
    val minute = parts.getOrNull(1)?.toIntOrNull()
    if (parts.size != 2 || hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
        unreadable("time", value)
    }
    return LocalTime.of(hour, minute)
}

private fun window(from: String?, to: String?, what: String): ClockWindow? = when {
    from == null && to == null -> null
    from == null || to == null -> unreadable(what, "$from-$to")
    else -> ClockWindow(clock(from), clock(to))
}

private fun dateWindow(after: String?, before: String?): DateWindow? = when {
    after == null && before == null -> null

    after == null || before == null -> unreadable("after/before", "$after-$before")

    else -> DateWindow(
        Instant.ofEpochSecond(after.toLongOrNull() ?: unreadable("after", after)),
        Instant.ofEpochSecond(before.toLongOrNull() ?: unreadable("before", before))
    )
}

private fun offset(value: String): Offset {
    val parts = value.split(',').map { int("offset", it.trim()) }
    return when (parts.size) {
        1 -> Offset(parts[0], parts[0])
        2 -> Offset(parts[0], parts[1])
        else -> unreadable("offset", value)
    }
}

private fun filters(entries: List<Pair<String, String>>): Filters {
    val title = ArrayList<String>()
    val short = ArrayList<String>()
    val description = ArrayList<String>()
    val days = ArrayList<DayFilter>()
    entries.forEach { (where, value) ->
        when (where) {
            "title" -> title += value
            "shortdescription" -> short += value
            "description" -> description += value
            "dayofweek" -> days += DayFilter.of(value.trim()) ?: unreadable("dayofweek", value)
            else -> unreadable("where", where)
        }
    }
    return Filters(title, short, description, days)
}

private fun RawTimer.afterEvent(): AfterEvent {
    val fixed = afterEvents.map { (attributes, token) ->
        AfterEvent.Fixed(
            action = AfterEventAction.ofListToken(token) ?: unreadable("afterevent", token),
            window = window(attributes["from"], attributes["to"], "afterevent from/to")
        )
    }
    return when (fixed.size) {
        0 -> AfterEvent.ReceiverDefault
        1 -> fixed.single()
        else -> AfterEvent.Several(fixed)
    }
}

private fun duplicates(avoid: String?, compare: String?): DuplicateCheck {
    val scope = avoid?.let { int("avoidDuplicateDescription", it) } ?: 0
    if (scope == 0) {
        return DuplicateCheck.Off
    }
    val compareCode = compare?.let { int("searchForDuplicateDescription", it) } ?: 2
    return DuplicateCheck.On(
        scope = DuplicateScope.entries.firstOrNull { it.code == scope }
            ?: unreadable("avoidDuplicateDescription", avoid),
        compare = DescriptionCompare.entries.firstOrNull { it.code == compareCode }
            ?: unreadable("searchForDuplicateDescription", compare)
    )
}
