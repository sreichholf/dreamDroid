package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object SignalParser {
    fun parse(xml: String): Signal? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseSignal(parser)
        }
}

private fun parseSignal(parser: XmlPullParser): Signal? {
    val snrDb = StringBuilder()
    val snr = StringBuilder()
    val ber = StringBuilder()
    val agc = StringBuilder()
    var current: StringBuilder? = null
    var result: Signal? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2snrdb" -> current = snrDb

                    "e2snr" -> current = snr

                    "e2ber" -> current = ber

                    // OpenWebif historically emits the typo "e2acg"; accept both.
                    "e2acg", "e2agc" -> current = agc
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                when (parser.localTag()) {
                    "e2frontendstatus" -> result = finalizeSignal(snrDb, snr, ber, agc)
                    else -> current = null
                }
            }
        }
        event = parser.next()
    }
    if (result == null) {
        result = finalizeSignal(snrDb, snr, ber, agc)
    }
    return result
}

private fun finalizeSignal(
    snrDb: StringBuilder,
    snr: StringBuilder,
    ber: StringBuilder,
    agc: StringBuilder
): Signal? {
    val built = Signal(
        snrDbRaw = snrDb.toString().trim(),
        snrRaw = snr.toString().trim(),
        berRaw = ber.toString().trim(),
        agcRaw = agc.toString().trim()
    )
    return if (built.isEmpty()) null else built
}
