package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object VolumeParser {
    fun parse(xml: String): Volume? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseVolume(parser)
        }
}

private fun parseVolume(parser: XmlPullParser): Volume {
    val result = StringBuilder()
    val current = StringBuilder()
    val muted = StringBuilder()
    var target: StringBuilder? = null
    var sawResult = false
    var sawCurrent = false
    var sawMuted = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2result" -> {
                        target = result
                        sawResult = false
                    }

                    "e2current" -> {
                        target = current
                        sawCurrent = false
                    }

                    "e2ismuted" -> {
                        target = muted
                        sawMuted = false
                    }

                    else -> target = null
                }
            }

            XmlPullParser.TEXT -> if (target != null) {
                parser.appendText(target)
                when {
                    target === result -> sawResult = true
                    target === current -> sawCurrent = true
                    target === muted -> sawMuted = true
                }
            }

            XmlPullParser.END_TAG -> target = null
        }
        event = parser.next()
    }
    return Volume(
        result = if (sawResult) result.toString() else null,
        current = if (sawCurrent) current.toString() else null,
        muted = if (sawMuted) muted.toString() else null
    )
}
