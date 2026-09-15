package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object ServiceParser {
    fun parse(xml: String): List<Service> =
        parseEnigmaXml(xml, emptyResult = emptyList(), onFail = emptyList()) { parser ->
            parseServiceList(parser)
        }
}

private fun parseServiceList(parser: XmlPullParser): List<Service> {
    val services = ArrayList<Service>()
    val reference = StringBuilder()
    val name = StringBuilder()
    var current: StringBuilder? = null
    var inService = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2service" -> {
                        inService = true
                        reference.setLength(0)
                        name.setLength(0)
                        current = null
                    }

                    "e2servicereference" -> if (inService) current = reference

                    "e2servicename" -> if (inService) current = name
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                when (parser.localTag()) {
                    "e2service" -> {
                        inService = false
                        current = null
                        services.add(
                            Service(
                                reference.toString(),
                                name.toString().stripCntrl()
                            )
                        )
                    }

                    else -> current = null
                }
            }
        }
        event = parser.next()
    }
    return services
}
