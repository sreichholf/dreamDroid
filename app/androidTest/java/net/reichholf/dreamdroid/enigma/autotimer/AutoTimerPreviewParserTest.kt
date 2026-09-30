package net.reichholf.dreamdroid.enigma.autotimer

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import net.reichholf.dreamdroid.enigma.SimpleResultParser
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** AutoTimer previews and the Run now reply on the device's own XML parser. */
@RunWith(AndroidJUnit4::class)
class AutoTimerPreviewParserTest {
    @Test
    fun theTestReplyHasVerdictsAndLogsUnescapedTwice() {
        val matches = matches("test.xml")

        assertEquals(4, matches.size)
        val first = matches[0]
        assertEquals("Wilsberg - In Treu und Glauben", first.title)
        assertEquals(Instant.ofEpochSecond(1790791800), first.begin)
        assertEquals(Verdict.Record, first.verdict)
        // `&amp;#13;&amp;#10;` in the reply is a line break once decoded twice.
        assertEquals(
            "possible epgmatch Wilsberg - In Treu und Glauben\n" +
                "Serviceref 1:0:19:2B7A:3F3:1:C00000:0:0:0:",
            first.log
        )
    }

    @Test
    fun theSimulateReplyHasNoVerdict() {
        val matches = matches("simulate.xml")

        assertEquals(4, matches.size)
        assertNull(matches[1].verdict)
    }

    /** Synthetic: the plugin's failure output appended to a captured reply. */
    @Test
    fun aPluginExceptionIsReportedWithItsText() {
        val xml = loadWebFixture("autotimer/test.xml").removeSuffix("</e2autotimersimulate>") +
            "<exception>list index out of range</exception><|PURPOSEFULLYBROKENXML<"

        assertEquals(
            PreviewOutcome.PluginFailed("list index out of range"),
            AutoTimerPreviewParser.parse(xml)
        )
    }

    /** Synthetic: `/autotimer/parse` sends `<ignore />` while it searches, then the result. */
    @Test
    fun runNowReadsTheSummaryPastTheKeepAlives() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><e2simplexmlresult>" +
            "<ignore /><ignore /><e2state>True</e2state>\n\t<e2statetext>Found a total of 4 " +
            "matching Events.</e2statetext></e2simplexmlresult>"

        val result = SimpleResultParser.parse(xml)!!

        assertEquals("True", result.state)
        assertEquals("Found a total of 4 matching Events.", result.stateText)
    }

    private fun matches(fixture: String): List<PreviewMatch> = (
        AutoTimerPreviewParser.parse(
            loadWebFixture("autotimer/$fixture")
        ) as PreviewOutcome.Matches
        )
        .matches
}
