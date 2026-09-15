package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object PowerStateParser {
    fun parse(xml: String): PowerState? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parsePowerState(parser)
        }
}

private fun parsePowerState(parser: XmlPullParser): PowerState {
    val standbyRaw = StringBuilder()
    var current: StringBuilder? = null
    var isRunning: Boolean? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                if (parser.localTag() == "e2instandby") {
                    standbyRaw.setLength(0)
                    current = standbyRaw
                } else {
                    current = null
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                if (parser.localTag() == "e2instandby") {
                    current = null
                    isRunning = when (standbyRaw.toString().trim()) {
                        "false" -> true
                        "true" -> false
                        else -> isRunning
                    }
                } else {
                    current = null
                }
            }
        }
        event = parser.next()
    }
    return PowerState(isRunning)
}
