package net.reichholf.dreamdroid.video

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PreferredAudioTrackTest {
    /** Media3 on a French device: names in French, codes from the stream. */
    private val media3Tracks = listOf(
        AudioTrackOption(1, "anglais · AAC", "en"),
        AudioTrackOption(2, "allemand · AC3", "de")
    )

    /** VLC: names are "Track n - [Language]", codes are ISO 639-2/B. */
    private val vlcTracks = listOf(
        AudioTrackOption(1, "Track 1 - [English]", "eng"),
        AudioTrackOption(2, "Track 2 - [German]", "ger")
    )

    @ParameterizedTest
    @ValueSource(strings = ["de", "deu", "ger", "German", " DE "])
    fun germanQueriesMatchTheGermanTrackByCode(query: String) {
        assertEquals(2, preferredAudioTrackId(query, media3Tracks, Locale.FRENCH))
        assertEquals(2, preferredAudioTrackId(query, vlcTracks, Locale.ENGLISH))
    }

    @Test
    fun theNameInTheDeviceLanguageMatchesByCode() {
        assertEquals(2, preferredAudioTrackId("Deutsch", vlcTracks, Locale.GERMAN))
        assertEquals(2, preferredAudioTrackId("deutsch", media3Tracks, Locale.GERMAN))
        assertNull(preferredAudioTrackId("Deutsch", vlcTracks, Locale.FRENCH))
    }

    @ParameterizedTest
    @ValueSource(strings = ["en", "eng", "English"])
    fun englishQueriesMatchTheEnglishTrackByCode(query: String) {
        assertEquals(1, preferredAudioTrackId(query, media3Tracks, Locale.FRENCH))
        assertEquals(1, preferredAudioTrackId(query, vlcTracks, Locale.GERMAN))
    }

    @Test
    fun theDeviceLanguageNameMatches() {
        assertEquals(2, preferredAudioTrackId("allemand", media3Tracks, Locale.FRENCH))
    }

    @Test
    fun aCodeMatchesTheLanguageNameOfATrackWithoutCode() {
        val tracks = listOf(
            AudioTrackOption(1, "Track 1 - [English]"),
            AudioTrackOption(2, "Track 2 - [German]")
        )

        assertEquals(2, preferredAudioTrackId("de", tracks, Locale.ENGLISH))
        assertEquals(2, preferredAudioTrackId("ger", tracks, Locale.ENGLISH))
        assertEquals(2, preferredAudioTrackId("Deutsch", tracks, Locale.GERMAN))
    }

    @Test
    fun partOfATrackNameMatchesAsBefore() {
        val tracks = listOf(
            AudioTrackOption(1, "French"),
            AudioTrackOption(2, "English (AAC)")
        )

        assertEquals(2, preferredAudioTrackId("lish", tracks, Locale.ENGLISH))
        assertEquals(2, preferredAudioTrackId("AAC", tracks, Locale.ENGLISH))
    }

    @Test
    fun noMatchKeepsTheStreamDefault() {
        assertNull(preferredAudioTrackId("fr", vlcTracks, Locale.ENGLISH))
        assertNull(preferredAudioTrackId("Klingon", media3Tracks, Locale.ENGLISH))
        assertNull(preferredAudioTrackId("en", emptyList(), Locale.ENGLISH))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun aBlankQueryKeepsTheStreamDefault(query: String) {
        assertNull(preferredAudioTrackId(query, vlcTracks, Locale.ENGLISH))
    }
}
