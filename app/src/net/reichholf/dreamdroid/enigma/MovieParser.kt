package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

object MovieParser {
    fun parse(xml: String): List<Movie> {
        if (xml.isEmpty()) {
            return emptyList()
        }
        return parseSanitized(xml, aggressive = false)
            ?: parseSanitized(xml, aggressive = true)
            ?: emptyList()
    }

    private fun parseSanitized(xml: String, aggressive: Boolean): List<Movie>? {
        return try {
            val handler = MovieListHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(XmlInput.sanitize(xml, aggressive))))
            handler.movies
        } catch (e: Exception) {
            null
        }
    }
}

private class MovieListHandler : DefaultHandler() {
    val movies = ArrayList<Movie>()

    private var inMovie = false
    private var inReference = false
    private var inTitle = false
    private var inDescription = false
    private var inDescriptionEx = false
    private var inName = false
    private var inTime = false
    private var inLength = false
    private var inTags = false
    private var inFilename = false
    private var inFilesize = false

    private val reference = StringBuilder()
    private val title = StringBuilder()
    private val description = StringBuilder()
    private val descriptionExtended = StringBuilder()
    private val serviceName = StringBuilder()
    private val time = StringBuilder()
    private val length = StringBuilder()
    private val tags = StringBuilder()
    private val fileName = StringBuilder()
    private val fileSize = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (tag(localName, qName)) {
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
            }
            "e2servicereference" -> inReference = true
            "e2title" -> inTitle = true
            "e2description" -> inDescription = true
            "e2descriptionextended" -> inDescriptionEx = true
            "e2servicename" -> inName = true
            "e2time" -> inTime = true
            "e2length" -> inLength = true
            "e2tags" -> inTags = true
            "e2filename" -> inFilename = true
            "e2filesize" -> inFilesize = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (tag(localName, qName)) {
            "e2movie" -> {
                inMovie = false
                movies.add(buildMovie())
            }
            "e2servicereference" -> inReference = false
            "e2title" -> inTitle = false
            "e2description" -> inDescription = false
            "e2descriptionextended" -> inDescriptionEx = false
            "e2servicename" -> inName = false
            "e2time" -> inTime = false
            "e2length" -> inLength = false
            "e2tags" -> inTags = false
            "e2filename" -> inFilename = false
            "e2filesize" -> inFilesize = false
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (!inMovie) {
            return
        }
        when {
            inReference -> reference.append(ch, start, length)
            inTitle -> title.append(ch, start, length)
            inDescription -> description.append(ch, start, length)
            inDescriptionEx -> descriptionExtended.append(ch, start, length)
            inName -> serviceName.append(ch, start, length)
            inTime -> time.append(ch, start, length)
            inLength -> this.length.append(ch, start, length)
            inTags -> tags.append(ch, start, length)
            inFilename -> fileName.append(ch, start, length)
            inFilesize -> fileSize.append(ch, start, length)
        }
    }

    private fun buildMovie(): Movie {
        val timeRaw = time.toString()
        var sizeRaw = fileSize.toString()
        var sizeReadable = ""
        if (sizeRaw.isNotEmpty()) {
            var forCalc = sizeRaw
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
            reference = reference.toString(),
            title = title.toString(),
            description = description.toString(),
            descriptionExtended = descriptionExtended.toString(),
            serviceName = serviceName.toString().replace("\\p{Cntrl}".toRegex(), ""),
            time = timeRaw,
            timeReadable = if (timeRaw.isNotEmpty()) DateTime.getDateTimeString(timeRaw) else "",
            length = length.toString(),
            tags = tags.toString(),
            fileName = fileName.toString(),
            fileSize = sizeRaw,
            fileSizeReadable = sizeReadable,
        )
    }

    private fun tag(localName: String?, qName: String?): String {
        val raw = if (!localName.isNullOrEmpty()) localName else (qName ?: "")
        val colon = raw.lastIndexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }
}
