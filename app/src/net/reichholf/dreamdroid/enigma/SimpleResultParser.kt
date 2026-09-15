package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

/**
 * Typed `/web` simple result (`e2state`/`e2result` + `e2statetext`/`e2resulttext`).
 */
object SimpleResultParser {
    fun parse(xml: String): SimpleResult? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseSimpleResult(parser)
        }
}

private fun parseSimpleResult(parser: XmlPullParser): SimpleResult {
    val state = StringBuilder()
    val stateText = StringBuilder()
    var current: StringBuilder? = null
    var sawState = false
    var sawStateText = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2state", "e2result" -> {
                        current = state
                        sawState = false
                    }

                    "e2statetext", "e2resulttext" -> {
                        current = stateText
                        sawStateText = false
                    }

                    else -> current = null
                }
            }

            XmlPullParser.TEXT -> if (current != null) {
                parser.appendText(current)
                if (current === state) {
                    sawState = true
                } else if (current === stateText) {
                    sawStateText = true
                }
            }

            XmlPullParser.END_TAG -> current = null
        }
        event = parser.next()
    }
    return SimpleResult(
        state = if (sawState) state.toString() else null,
        stateText = if (sawStateText) stateText.toString() else null
    )
}
