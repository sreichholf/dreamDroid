package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

/**
 * `/web/external` of the Dreambox web interface: each plugin page's `e2path` with its
 * `e2externalversion`, "" without one. A plugin registers its API version there; the AutoTimer
 * plugin its `api_version` (`plugin.py`, `addExternalChild`). Null when the XML does not parse.
 */
object WebExternalsParser {
    fun parse(xml: String): Map<String, String>? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser -> parseExternals(parser) }
}

private fun parseExternals(parser: XmlPullParser): Map<String, String> {
    val externals = LinkedHashMap<String, String>()
    val path = StringBuilder()
    val version = StringBuilder()
    var current: StringBuilder? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> current = when (parser.localTag()) {
                "e2webifexternal" -> {
                    path.setLength(0)
                    version.setLength(0)
                    null
                }

                "e2path" -> path

                "e2externalversion" -> version

                else -> null
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                if (parser.localTag() == "e2webifexternal") {
                    externals[path.toString().trim()] = version.toString().trim()
                }
                current = null
            }
        }
        event = parser.next()
    }
    return externals
}
