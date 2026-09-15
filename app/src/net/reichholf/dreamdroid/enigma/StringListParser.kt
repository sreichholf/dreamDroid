package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object StringListParser {
    fun parse(xml: String, itemTag: String): List<String>? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseStringList(parser, itemTag)
        }
}

private fun parseStringList(parser: XmlPullParser, itemTag: String): List<String> {
    val items = ArrayList<String>()
    val item = StringBuilder()
    var inItem = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> if (parser.localTag() == itemTag) {
                inItem = true
                item.setLength(0)
            }

            XmlPullParser.TEXT -> if (inItem) {
                parser.appendText(item)
            }

            XmlPullParser.END_TAG -> if (parser.localTag() == itemTag) {
                inItem = false
                items.add(item.toString().trim())
            }
        }
        event = parser.next()
    }
    return items
}
