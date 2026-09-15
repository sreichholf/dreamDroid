package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.enigma2.Request
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleXmlParsersTest {
    @Test
    fun simpleResultAcceptsStateAndResultTags() {
        val state = SimpleResultParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2simplexmlresult>
            <e2state>True</e2state>
            <e2statetext>OK</e2statetext>
            </e2simplexmlresult>
            """.trimIndent()
        )
        assertEquals("True", state!!.state)
        assertEquals("OK", state.stateText)

        val result = SimpleResultParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2simplexmlresult>
            <e2result>False</e2result>
            <e2resulttext>Nope</e2resulttext>
            </e2simplexmlresult>
            """.trimIndent()
        )
        assertEquals("False", result!!.state)
        assertEquals("Nope", result.stateText)
    }

    @Test
    fun simpleResultConcatenatesSplitText() {
        val parsed = SimpleResultParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2simplexmlresult>
            <e2state>Tr<!--x-->ue</e2state>
            <e2statetext>Hel<!--x-->lo</e2statetext>
            </e2simplexmlresult>
            """.trimIndent()
        )
        assertEquals("True", parsed!!.state)
        assertEquals("Hello", parsed.stateText)
    }

    @Test
    fun simpleResultSanitizesNbspAndControlChars() {
        val parsed = SimpleResultParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2simplexmlresult>
            <e2state>True</e2state>
            <e2statetext>OK\u0001&nbsp;now</e2statetext>
            </e2simplexmlresult>
            """.trimIndent().replace("\\u0001", "\u0001")
        )
        assertEquals("OK now", parsed!!.stateText)
        assertFalse(parsed.stateText!!.contains("\u0001"))
    }

    @Test
    fun requestHandlerCopiesSimpleResultFields() {
        val handler = SimpleResultRequestHandler("/web/message")
        val parsed = handler.parseSimpleResult(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2simplexmlresult>
            <e2state>True</e2state>
            <e2statetext>Done</e2statetext>
            </e2simplexmlresult>
            """.trimIndent()
        )
        assertEquals("True", parsed.state)
        assertEquals("Done", parsed.stateText)
    }

    @Test
    fun volumeParsesResultCurrentMuted() {
        val volume = VolumeParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2volume>
            <e2result>True</e2result>
            <e2current>40</e2current>
            <e2ismuted>False</e2ismuted>
            </e2volume>
            """.trimIndent()
        )
        assertNotNull(volume)
        assertEquals("True", volume!!.result)
        assertEquals("40", volume.current)
        assertEquals("False", volume.muted)

        val fromHandler = VolumeRequestHandler().parseVolume(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2volume>
            <e2result>True</e2result>
            <e2current>40</e2current>
            <e2ismuted>False</e2ismuted>
            </e2volume>
            """.trimIndent()
        )
        assertEquals("40", fromHandler.current)
    }

    @Test
    fun powerStateInvertsInStandbyString() {
        val off = PowerStateParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2powerstate><e2instandby>false</e2instandby></e2powerstate>
            """.trimIndent()
        )
        assertEquals(true, off!!.isRunning)

        val on = PowerStateParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2powerstate><e2instandby>true</e2instandby></e2powerstate>
            """.trimIndent()
        )
        assertEquals(false, on!!.isRunning)
    }

    @Test
    fun sleepTimerParsesNestedTags() {
        val parsed = SleepTimerParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2sleeptimer>
            <e2enabled>True</e2enabled>
            <e2minutes>30</e2minutes>
            <e2action>standby</e2action>
            <e2text>OK</e2text>
            </e2sleeptimer>
            """.trimIndent()
        )
        assertEquals("True", parsed!!.enabled)
        assertEquals("30", parsed.minutes)
        assertEquals("standby", parsed.action)
        assertEquals("OK", parsed.text)
    }

    @Test
    fun stringListParsesLocationsAndTags() {
        val locations = StringListParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2locations>
            <e2location>/hdd/movie/</e2location>
            <e2location>/media/hdd/</e2location>
            </e2locations>
            """.trimIndent(),
            "e2location"
        )
        assertEquals(listOf("/hdd/movie/", "/media/hdd/"), locations)

        val tags = StringListParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2tags>
            <e2tag>news</e2tag>
            <e2tag>sport</e2tag>
            </e2tags>
            """.trimIndent(),
            "e2tag"
        )
        assertEquals(listOf("news", "sport"), tags)
    }

    @Test
    fun stringListConcatenatesSplitTextAndTrims() {
        val locations = StringListParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2locations>
            <e2location>  /hdd/<!--x-->movie/  </e2location>
            </e2locations>
            """.trimIndent(),
            "e2location"
        )
        assertEquals(listOf("/hdd/movie/"), locations)
    }

    @Test
    fun malformedSimpleResultUsesDefault() {
        assertNull(SimpleResultParser.parse("<e2simplexmlresult><e2state>"))
        val fallback = SimpleResultRequestHandler("/web/message")
            .parseSimpleResult("<e2simplexmlresult><e2state>")
        assertEquals("False", fallback.state)
        assertNull(fallback.stateText)
    }

    @Test
    fun requestParseListFillsLocations() {
        val list = ArrayList<String>()
        assertTrue(
            Request.parseList(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <e2locations><e2location>/hdd/movie/</e2location></e2locations>
                """.trimIndent(),
                list,
                "e2location"
            )
        )
        assertEquals(listOf("/hdd/movie/"), list)
    }
}
