package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import org.xmlpull.v1.XmlPullParser

object MovieParser {
    /**
     * @return parsed movies, or null when XML cannot be parsed (distinct from a valid empty list).
     */
    fun parse(xml: String): List<Movie>? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseMovieList(parser)
        }
}

private fun parseMovieList(parser: XmlPullParser): List<Movie> {
    val movies = ArrayList<Movie>()
    val reference = StringBuilder()
    val title = StringBuilder()
    val description = StringBuilder()
    val descriptionExtended = StringBuilder()
    val serviceName = StringBuilder()
    val time = StringBuilder()
    val length = StringBuilder()
    val tags = StringBuilder()
    val fileName = StringBuilder()
    val fileSize = StringBuilder()
    var current: StringBuilder? = null
    var inMovie = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2movie" -> {
                        inMovie = true
                        reference.setLength(0)
                        title.setLength(0)
                        description.setLength(0)
                        descriptionExtended.setLength(0)
                        serviceName.setLength(0)
                        time.setLength(0)
                        length.setLength(0)
                        tags.setLength(0)
                        fileName.setLength(0)
                        fileSize.setLength(0)
                        current = null
                    }

                    "e2servicereference" -> if (inMovie) current = reference

                    "e2title" -> if (inMovie) current = title

                    "e2description" -> if (inMovie) current = description

                    "e2descriptionextended" -> if (inMovie) current = descriptionExtended

                    "e2servicename" -> if (inMovie) current = serviceName

                    "e2time" -> if (inMovie) current = time

                    "e2length" -> if (inMovie) current = length

                    "e2tags" -> if (inMovie) current = tags

                    "e2filename" -> if (inMovie) current = fileName

                    "e2filesize" -> if (inMovie) current = fileSize
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                when (parser.localTag()) {
                    "e2movie" -> {
                        inMovie = false
                        current = null
                        movies.add(
                            buildMovie(
                                reference = reference.toString(),
                                title = title.toString(),
                                description = description.toString(),
                                descriptionExtended = descriptionExtended.toString(),
                                serviceName = serviceName.toString(),
                                timeRaw = time.toString(),
                                length = length.toString(),
                                tags = tags.toString(),
                                fileName = fileName.toString(),
                                fileSizeRaw = fileSize.toString()
                            )
                        )
                    }

                    else -> current = null
                }
            }
        }
        event = parser.next()
    }
    return movies
}

private fun buildMovie(
    reference: String,
    title: String,
    description: String,
    descriptionExtended: String,
    serviceName: String,
    timeRaw: String,
    length: String,
    tags: String,
    fileName: String,
    fileSizeRaw: String
): Movie {
    var sizeReadable = ""
    if (fileSizeRaw.isNotEmpty()) {
        var forCalc = fileSizeRaw
        if (Python.NONE == forCalc) {
            forCalc = "0"
        }
        try {
            var size = forCalc.toLong()
            size /= (1024 * 1024)
            sizeReadable = "$size MB"
        } catch (e: NumberFormatException) {
            sizeReadable = ""
        }
    }
    return Movie(
        reference = reference,
        title = title,
        description = description,
        descriptionExtended = descriptionExtended,
        serviceName = serviceName.stripCntrl(),
        time = timeRaw,
        timeReadable = if (timeRaw.isNotEmpty()) DateTime.getDateTimeString(timeRaw) else "",
        length = length,
        tags = tags,
        fileName = fileName,
        fileSize = fileSizeRaw,
        fileSizeReadable = sizeReadable
    )
}
