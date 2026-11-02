package net.reichholf.dreamdroid.video

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Media3TracksTest {
    @Test
    fun anIdCarriesTheGroupAndTheTrackInsideIt() {
        assertEquals(0, Media3Tracks.id(0, 0))
        assertEquals(3, Media3Tracks.id(0, 3))
        assertEquals(1 shl 16, Media3Tracks.id(1, 0))
    }

    @Test
    fun everyIdSplitsBackIntoItsGroupAndTrack() {
        for (group in listOf(0, 1, 7, 1_234)) {
            for (track in listOf(0, 1, 9, 0xFFFE)) {
                val id = Media3Tracks.id(group, track)
                assertEquals(group, Media3Tracks.groupIndex(id), "group of $group/$track")
                assertEquals(track, Media3Tracks.trackIndex(id), "track of $group/$track")
            }
        }
    }

    @Test
    fun offIsNoTrack() {
        assertEquals(-1, Media3Tracks.OFF)
    }

    @Test
    fun theLabelComesFirstThenTheLanguageThenTheCodec() {
        assertEquals("Main", Media3Tracks.label("Main", "de", null))
        assertEquals("Main · AC3", Media3Tracks.label("Main", "de", "audio/ac3"))
        assertEquals(language("de"), Media3Tracks.label(null, "de", null))
    }

    @Test
    fun aBlankLabelFallsBackToTheLanguage() {
        assertEquals("${language("de")} · AC3", Media3Tracks.label("  ", "de", "audio/ac3"))
    }

    @Test
    fun theCodecAloneIsEnough() {
        assertEquals("AC3", Media3Tracks.label(null, null, "audio/ac3"))
    }

    @Test
    fun nothingKnownIsAnEmptyLabel() {
        assertEquals("", Media3Tracks.label(null, null, null))
        assertEquals("", Media3Tracks.label(" ", "", ""))
    }

    /** What the dialog shows, in whichever locale the test runs. */
    private fun language(tag: String): String = Locale.forLanguageTag(tag).displayLanguage
}
