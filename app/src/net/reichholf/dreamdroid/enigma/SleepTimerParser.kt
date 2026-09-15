package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object SleepTimerParser {
    fun parse(xml: String): SleepTimer? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseSleepTimer(parser)
        }
}

private fun parseSleepTimer(parser: XmlPullParser): SleepTimer {
    val enabled = StringBuilder()
    val minutes = StringBuilder()
    val action = StringBuilder()
    val text = StringBuilder()
    var current: StringBuilder? = null
    var inSleeptimer = false
    var sawEnabled = false
    var sawMinutes = false
    var sawAction = false
    var sawText = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                val tag = parser.localTag()
                if (tag == "e2sleeptimer") {
                    inSleeptimer = true
                    current = null
                } else if (inSleeptimer) {
                    when (tag) {
                        "e2enabled" -> {
                            current = enabled
                            sawEnabled = false
                        }

                        "e2minutes" -> {
                            current = minutes
                            sawMinutes = false
                        }

                        "e2action" -> {
                            current = action
                            sawAction = false
                        }

                        "e2text" -> {
                            current = text
                            sawText = false
                        }

                        else -> current = null
                    }
                }
            }

            XmlPullParser.TEXT -> if (current != null) {
                parser.appendText(current)
                when {
                    current === enabled -> sawEnabled = true
                    current === minutes -> sawMinutes = true
                    current === action -> sawAction = true
                    current === text -> sawText = true
                }
            }

            XmlPullParser.END_TAG -> {
                if (parser.localTag() == "e2sleeptimer") {
                    inSleeptimer = false
                }
                current = null
            }
        }
        event = parser.next()
    }
    return SleepTimer(
        enabled = if (sawEnabled) enabled.toString() else null,
        minutes = if (sawMinutes) minutes.toString() else null,
        action = if (sawAction) action.toString() else null,
        text = if (sawText) text.toString() else null
    )
}
