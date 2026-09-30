package net.reichholf.dreamdroid.enigma.autotimer

import java.time.Instant
import net.reichholf.dreamdroid.enigma.appendText
import net.reichholf.dreamdroid.enigma.localTag
import net.reichholf.dreamdroid.enigma.parseEnigmaXml
import org.xmlpull.v1.XmlPullParser

/** One event an AutoTimer would record, or skip, from `/autotimer/test` or `simulate`. */
data class PreviewMatch(
    val serviceRef: String,
    val serviceName: String,
    val title: String,
    val begin: Instant,
    val end: Instant,
    val autoTimerName: String,
    /** Null from `simulate`, which reports no verdict. */
    val verdict: Verdict?,
    /** The plugin's search log for the event; empty from `simulate`. */
    val log: String
)

enum class Verdict {
    Record,
    Skip
}

sealed interface PreviewOutcome {
    data class Matches(val matches: List<PreviewMatch>) : PreviewOutcome

    /** The plugin failed while searching and reported [message]. */
    data class PluginFailed(val message: String) : PreviewOutcome
}

/**
 * `<e2autotimersimulate>`. A failing plugin ends the document with `<exception>` and text it
 * does not escape, followed by deliberately broken XML, so that is read from the raw text.
 */
object AutoTimerPreviewParser {
    fun parse(xml: String): PreviewOutcome? {
        val exception = EXCEPTION.find(xml)
        if (exception != null) {
            return PreviewOutcome.PluginFailed(exception.groupValues[1].trim())
        }
        return parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseMatches(parser)
        }?.let { PreviewOutcome.Matches(it) }
    }

    private val EXCEPTION = Regex("<exception>(.*?)</exception>", RegexOption.DOT_MATCHES_ALL)
}

private fun parseMatches(parser: XmlPullParser): List<PreviewMatch>? {
    var sawRoot = false
    val matches = ArrayList<PreviewMatch>()
    var fields: MutableMap<String, String>? = null
    val text = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                text.setLength(0)
                when (parser.localTag()) {
                    "e2autotimersimulate" -> sawRoot = true
                    "e2simulatedtimer" -> fields = HashMap()
                }
            }

            XmlPullParser.TEXT -> parser.appendText(text)

            XmlPullParser.END_TAG -> {
                val current = fields
                val tag = parser.localTag()
                if (current != null) {
                    if (tag == "e2simulatedtimer") {
                        current.toMatch()?.let { matches += it }
                        fields = null
                    } else {
                        current[tag] = text.toString()
                    }
                }
                text.setLength(0)
            }
        }
        event = parser.next()
    }
    return matches.takeIf { sawRoot }
}

/** Null when a time is missing; such a row cannot be placed on the timeline. */
private fun Map<String, String>.toMatch(): PreviewMatch? {
    val begin = this["e2timebegin"]?.trim()?.toLongOrNull() ?: return null
    val end = this["e2timeend"]?.trim()?.toLongOrNull() ?: return null
    return PreviewMatch(
        serviceRef = this["e2servicereference"].orEmpty().trim(),
        serviceName = this["e2servicename"].orEmpty().trim(),
        title = this["e2name"].orEmpty().trim(),
        begin = Instant.ofEpochSecond(begin),
        end = Instant.ofEpochSecond(end),
        autoTimerName = this["e2autotimername"].orEmpty().trim(),
        verdict = when (this["e2state"]?.trim()) {
            "OK" -> Verdict.Record
            "Skip" -> Verdict.Skip
            else -> null
        },
        log = this["e2message"].orEmpty().unescapeLineBreaks().trim()
    )
}

/** The plugin escapes the log's line breaks twice; one XML parse leaves `&#13;&#10;`. */
private fun String.unescapeLineBreaks(): String =
    replace("&#13;&#10;", "\n").replace("&#10;", "\n").replace("&#13;", "\n")
