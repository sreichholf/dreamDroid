package net.reichholf.dreamdroid.enigma

import java.io.ByteArrayInputStream
import java.io.StringReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Branches of [parseEnigmaXml] and [EnigmaXmlPullParser] that the parsers' own tests do not pin:
 * sanitize retry, fallback results, prolog handling and the unused pull-parser contract.
 */
class XmlPullEdgeCaseTest {
    @Test
    fun emptyDocumentReturnsEmptyResultWithoutRunningBlock() {
        val empty = emptyList<String>()
        var blockRan = false
        val result = parseEnigmaXml("", emptyResult = empty) {
            blockRan = true
            emptyList()
        }
        assertSame(empty, result)
        assertFalse(blockRan)
    }

    @Test
    fun whitespaceOnlyDocumentYieldsOneWhitespaceTextEvent() {
        val events = ArrayList<Int>()
        var whitespace = false
        val result = parseEnigmaXml("   \n\t ", emptyList<Int>()) { parser ->
            var event = parser.next()
            while (event != XmlPullParser.END_DOCUMENT) {
                events.add(event)
                if (event == XmlPullParser.TEXT) {
                    whitespace = parser.isWhitespace()
                }
                event = parser.next()
            }
            events
        }
        assertEquals(listOf(XmlPullParser.TEXT), result)
        assertTrue(whitespace)
    }

    @Test
    fun truncatedDocumentFallsBackToOnFail() {
        var attempts = 0
        val result = parseEnigmaXml("<a><b>", emptyResult = "empty", onFail = "fail") {
            attempts++
            drain(it)
            "ok"
        }
        assertEquals("fail", result)
        assertEquals(2, attempts)
    }

    @Test
    fun mismatchedEndTagFallsBackToOnFail() {
        val result = parseEnigmaXml("<a></b>", emptyResult = "empty", onFail = "fail") {
            drain(it)
            "ok"
        }
        assertEquals("fail", result)
    }

    @Test
    fun blockThatLeavesDepthOpenFallsBackToOnFail() {
        val result = parseEnigmaXml("<a><b/></a>", emptyResult = "empty", onFail = "fail") {
            it.next()
            "partial"
        }
        assertEquals("fail", result)
    }

    @Test
    fun aggressiveRetryRecoversControlCharInsideTagName() {
        var attempts = 0
        val result = parseEnigmaXml("<a\u0080></a>", emptyResult = "empty", onFail = "fail") {
            attempts++
            drain(it)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun nestedTagsReportIncreasingDepth() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a><b/></a>"))
        assertEquals(0, parser.depth)
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(1, parser.depth)
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(2, parser.depth)
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertEquals(1, parser.depth)
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertEquals(0, parser.depth)
    }

    @Test
    fun repeatedItemsAreCollectedAsSeparateEntries() {
        val parsed = StringListParser.parse(
            "<e2stringlist><e2string>one</e2string><e2string>two</e2string></e2stringlist>",
            "e2string"
        )
        assertEquals(listOf("one", "two"), parsed)
    }

    @Test
    fun nestedTagTextIsJoinedIntoEnclosingItem() {
        val parsed = StringListParser.parse(
            "<e2stringlist><e2string>a<b>c</b>d</e2string></e2stringlist>",
            "e2string"
        )
        assertEquals(listOf("acd"), parsed)
    }

    @Test
    fun textSplitByPiAndCdataIsJoined() {
        val parsed = StringListParser.parse(
            "<e2stringlist><e2string>x<?pi?>y<![CDATA[z]]></e2string></e2stringlist>",
            "e2string"
        )
        assertEquals(listOf("xyz"), parsed)
    }

    @Test
    fun appendTextSkipsEventsWithoutText() {
        val parser = EnigmaXmlPullParser()
        val target = StringBuilder("x")
        parser.setInput(StringReader("<a>hi</a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        parser.appendText(target)
        assertEquals("x", target.toString())
        assertEquals(XmlPullParser.TEXT, parser.next())
        parser.appendText(target)
        assertEquals("xhi", target.toString())
        assertEquals(XmlPullParser.END_TAG, parser.next())
        parser.appendText(target)
        assertEquals("xhi", target.toString())
    }

    @Test
    fun cdataIsEmittedAsSingleTextEvent() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a><![CDATA[x<y & z]]></a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(XmlPullParser.TEXT, parser.next())
        assertEquals("x<y & z", parser.text)
        assertEquals(XmlPullParser.END_TAG, parser.next())
    }

    @Test
    fun characterEntitiesAreDecodedInTextAndAttributes() {
        val parsed = StringListParser.parse(
            "<e2stringlist><e2string>a &amp; &#65;&#x42;</e2string></e2stringlist>",
            "e2string"
        )
        assertEquals(listOf("a & AB"), parsed)

        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a n=\"&#65;\" m='&apos;'/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("A", parser.getAttributeValue(0))
        assertEquals("'", parser.getAttributeValue(1))
    }

    @Test
    fun unknownOrUnterminatedEntityRejectsDocument() {
        val unknown = StringListParser.parse(
            "<e2stringlist><e2string>&bogus;</e2string></e2stringlist>",
            "e2string"
        )
        val unterminated = StringListParser.parse(
            "<e2stringlist><e2string>&amp</e2string></e2stringlist>",
            "e2string"
        )
        assertNull(unknown)
        assertNull(unterminated)
    }

    @Test
    fun unterminatedCdataThrows() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a><![CDATA[x</a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertThrows(XmlPullParserException::class.java) { parser.next() }
    }

    @Test
    fun prologDeclarationsCommentsAndDoctypeAreSkipped() {
        val xml = """<?xml version="1.0"?><!-- c --><!DOCTYPE r [ <!ENTITY e ">"> ]><a>t</a>"""
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader(xml))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("a", parser.name)
        assertEquals(XmlPullParser.TEXT, parser.next())
        assertEquals("t", parser.text)
    }

    @Test
    fun unterminatedDoctypeThrows() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<!DOCTYPE r ["))
        assertThrows(XmlPullParserException::class.java) { parser.next() }
    }

    @Test
    fun byteOrderMarkIsSkipped() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("\uFEFF<a/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("a", parser.name)
    }

    @Test
    fun crLfAndLoneCrNormalizeToLf() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a>l1\r\nl2\rdone</a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(XmlPullParser.TEXT, parser.next())
        assertEquals("l1\nl2\ndone", parser.text)
    }

    @Test
    fun emptyElementTagIsReportedBeforeItsEndTag() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a/><b></b>"))
        assertFalse(parser.isEmptyElementTag())
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertTrue(parser.isEmptyElementTag())
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertFalse(parser.isEmptyElementTag())
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertFalse(parser.isEmptyElementTag())
    }

    @Test
    fun nextTextReadsTextThenRequiresEndTag() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a>hello</a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("hello", parser.nextText())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        assertThrows(XmlPullParserException::class.java) { parser.nextText() }

        parser.setInput(StringReader("<a/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("", parser.nextText())

        parser.setInput(StringReader("<a><b/></a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertThrows(XmlPullParserException::class.java) { parser.nextText() }
    }

    @Test
    fun nextTagSkipsWhitespaceText() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a> <b/> </a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(XmlPullParser.START_TAG, parser.nextTag())
        assertEquals("b", parser.name)
        assertEquals(XmlPullParser.END_TAG, parser.nextTag())
        assertEquals(XmlPullParser.END_TAG, parser.nextTag())
        assertEquals("a", parser.name)
    }

    @Test
    fun nextTagRejectsNonWhitespaceText() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a>x</a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertThrows(XmlPullParserException::class.java) { parser.nextTag() }
    }

    @Test
    fun requireChecksEventTypeAndName() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        parser.require(XmlPullParser.START_TAG, null, "a")
        assertThrows(XmlPullParserException::class.java) {
            parser.require(XmlPullParser.START_TAG, null, "b")
        }
        assertThrows(XmlPullParserException::class.java) {
            parser.require(XmlPullParser.TEXT, null, null)
        }
    }

    @Test
    fun attributeMetadataAndOutOfRangeLookups() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a x=\"1\" y=\"2\"/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(2, parser.attributeCount)
        assertEquals(XmlPullParser.NO_NAMESPACE, parser.getAttributeNamespace(0))
        assertNull(parser.getAttributePrefix(0))
        assertEquals("CDATA", parser.getAttributeType(0))
        assertFalse(parser.isAttributeDefault(0))
        assertEquals("2", parser.getAttributeValue(null, "y"))
        assertNull(parser.getAttributeName(2))
        assertNull(parser.getAttributeValue(2))
    }

    @Test
    fun textCharactersFillsHolderAndIsNullOffText() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a>hi</a>"))
        assertNull(parser.getTextCharacters(null))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertNull(parser.getTextCharacters(null))
        assertEquals(XmlPullParser.TEXT, parser.next())
        val holder = IntArray(2)
        val chars = parser.getTextCharacters(holder)
        assertEquals("hi", String(chars!!))
        assertEquals(0, holder[0])
        assertEquals(2, holder[1])
    }

    @Test
    fun positionDescriptionTracksTextAcrossNewlines() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a>\n<b/></a>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(1, parser.lineNumber)
        assertEquals(XmlPullParser.TEXT, parser.next())
        assertEquals(2, parser.lineNumber)
        assertEquals(1, parser.columnNumber)
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("line 2 column 5", parser.positionDescription)
    }

    @Test
    fun parserHooksWithoutProductionUseAreStubbed() {
        val parser = EnigmaXmlPullParser()
        assertEquals("UTF-8", parser.inputEncoding)
        assertEquals(0, parser.getNamespaceCount(0))
        assertNull(parser.getNamespacePrefix(0))
        assertNull(parser.getNamespaceUri(0))
        assertEquals(XmlPullParser.NO_NAMESPACE, parser.getNamespace("p"))
        assertEquals(XmlPullParser.NO_NAMESPACE, parser.getNamespace())
        assertFalse(parser.getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES))
        assertNull(parser.getProperty("p"))
        assertThrows(XmlPullParserException::class.java) {
            parser.setProperty("p", 1)
        }
        assertThrows(XmlPullParserException::class.java) {
            parser.defineEntityReplacementText("e", "v")
        }
        assertThrows(XmlPullParserException::class.java) {
            parser.setInput(ByteArrayInputStream(ByteArray(0)), "UTF-8")
        }
    }

    @Test
    fun localTagStripsPrefixAfterLastColon() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<ns:e2snr>x</ns:e2snr>"))
        assertEquals("", parser.localTag())
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("ns", parser.prefix)
        assertEquals("ns:e2snr", parser.name)
        assertEquals("e2snr", parser.localTag())

        parser.setInput(StringReader("<a:b:c/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals("c", parser.localTag())

        parser.setInput(StringReader("<e2snr/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertNull(parser.prefix)
        assertEquals("e2snr", parser.localTag())
    }

    @Test
    fun stripCntrlRemovesControlCharacters() {
        assertEquals("abcd", "a\u0001b\tc\u007Fd".stripCntrl())
        assertEquals("", "\u0000\u001F".stripCntrl())
    }

    @Test
    fun newEnigmaPullParserFallsBackToJvmImplementation() {
        val parser = newEnigmaPullParser()
        assertTrue(parser is EnigmaXmlPullParser)
        parser.setInput(StringReader("<a/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
    }

    @Test
    fun nextAfterEndDocumentStaysAtEndDocument() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<a/>"))
        assertEquals(XmlPullParser.START_TAG, parser.next())
        assertEquals(XmlPullParser.END_TAG, parser.next())
        assertEquals(XmlPullParser.END_DOCUMENT, parser.next())
        assertEquals(XmlPullParser.END_DOCUMENT, parser.next())
    }
}

private fun drain(parser: XmlPullParser) {
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        event = parser.next()
    }
}
