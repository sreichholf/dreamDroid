package net.reichholf.dreamdroid.enigma.autotimer

import java.time.Instant
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** `/autotimer/test` and `simulate` replies captured from a dm900 with AutoTimer 4.3.2. */
class AutoTimerPreviewParserTest {
    @Test
    fun testReplyHasVerdictsAndReadableLogs() {
        val matches = matches("test.xml")

        assertEquals(4, matches.size)
        assertEquals(
            PreviewMatch(
                serviceRef = "1:0:19:2B7A:3F3:1:C00000:0:0:0:",
                serviceName = "zdf_neo HD",
                title = "Wilsberg - In Treu und Glauben",
                begin = Instant.ofEpochSecond(1790791800),
                end = Instant.ofEpochSecond(1790797800),
                autoTimerName = "dreamDroid test Wilsberg",
                verdict = Verdict.Record,
                log = "possible epgmatch Wilsberg - In Treu und Glauben\n" +
                    "Serviceref 1:0:19:2B7A:3F3:1:C00000:0:0:0:"
            ),
            matches[0]
        )
        assertEquals(listOf(Verdict.Record), matches.map { it.verdict }.distinct())
    }

    @Test
    fun simulateReplyHasNoVerdict() {
        val matches = matches("simulate.xml")

        assertEquals(4, matches.size)
        assertNull(matches[1].verdict)
        assertEquals("", matches[1].log)
        assertEquals("Wilsberg - Einfach weg", matches[1].title)
    }

    @Test
    fun nothingEnabledIsAnEmptyPreview() {
        assertEquals(emptyList<PreviewMatch>(), matches("simulate_empty.xml"))
    }

    @Test
    fun aSkippedEventIsMarked() {
        val xml = loadWebFixture("autotimer/test.xml").replaceFirst(
            "<e2state>OK</e2state>",
            "<e2state>Skip</e2state>"
        )

        val outcome = AutoTimerPreviewParser.parse(xml) as PreviewOutcome.Matches

        assertEquals(Verdict.Skip, outcome.matches[0].verdict)
    }

    /**
     * Synthetic: the plugin's failure output (`AutoTimerResource.py`, the test thread's
     * `except` branch) appended to a captured reply. Not captured from a box.
     */
    @Test
    fun aPluginExceptionIsReportedWithItsText() {
        val xml = loadWebFixture("autotimer/test.xml").removeSuffix("</e2autotimersimulate>") +
            "<exception>list index out of range</exception><|PURPOSEFULLYBROKENXML<"

        assertEquals(
            PreviewOutcome.PluginFailed("list index out of range"),
            AutoTimerPreviewParser.parse(xml)
        )
    }

    @Test
    fun anotherDocumentIsNotAPreview() {
        assertNull(AutoTimerPreviewParser.parse(loadWebFixture("autotimer/result_add.xml")))
    }

    private fun matches(fixture: String): List<PreviewMatch> = (
        AutoTimerPreviewParser.parse(loadWebFixture("autotimer/$fixture"))
            as PreviewOutcome.Matches
        ).matches
}
