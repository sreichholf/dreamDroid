package net.reichholf.dreamdroid.ui.video

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoPlaybackSessionTest {
    private val a = ServiceNowNext("1:0:1:a", "A", now = Event(title = "News A"))
    private val b = ServiceNowNext("1:0:1:b", "B", now = Event(title = "News B"))
    private val c = ServiceNowNext("1:0:1:c", "C", now = Event(title = "News C"))

    private val live = VideoPlaybackSession().withExtras("B", "1:0:1:b", "bouquet", null)

    @Test
    fun loadedListMakesTheMatchingRowThePlayingService() {
        val session = live.withServices(listOf(a, b, c))

        assertEquals(1, session.currentIndex)
        assertEquals("News B", session.currentService?.now?.title)
    }

    @Test
    fun neighbourWrapsAroundTheList() {
        val session = live.withServices(listOf(a, b, c)).zappedTo(c)

        assertSame(a, session.neighbour(forward = true))
        assertSame(b, session.neighbour(forward = false))
    }

    @Test
    fun sameExtrasKeepTheLoadedListAndPlayingRow() {
        val loaded = live.copy(bouquets = listOf(Service("bouquet", "TV")))
            .withServices(listOf(a, b, c))

        val again = loaded.withExtras("B", "1:0:1:b", "bouquet", null)

        assertEquals(loaded, again)
    }

    @Test
    fun recordingExtrasDropLiveInfoAndBouquets() {
        val loaded = live.copy(bouquets = listOf(Service("bouquet", "TV")))
            .withServices(listOf(a, b, c))

        val recording = loaded.withExtras("Film", null, null, Movie(title = "Film"))

        assertEquals("Film", recording.movie?.title)
        assertNull(recording.currentService)
        assertTrue(recording.bouquets.isEmpty())
        assertEquals(-1, recording.currentIndex)
    }

    @Test
    fun newRefsWithoutInfoForgetThePlayingRow() {
        val loaded = live.withServices(listOf(a, b, c))

        val other = loaded.withExtras("X", "1:0:1:x", "bouquet", null)

        assertEquals(VideoPlaying.Unknown, other.playing)
    }
}
