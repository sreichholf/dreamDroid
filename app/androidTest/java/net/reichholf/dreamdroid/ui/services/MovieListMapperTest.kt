package net.reichholf.dreamdroid.ui.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.MovieParser
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieListMapperTest {
    @Test
    fun mapsTypedMoviesToComposeItems() {
        val movies = MovieParser.parse(loadWebFixture("movielist.xml"))
        assertNotNull(movies)
        val items = movieListItemsFromMovies(movies!!)
        assertEquals(2, items.size)
        assertEquals("Evening News", items[0].title)
        assertEquals("100 MB", items[0].fileSize)
        assertEquals("Empty Size", items[1].title)
        assertEquals("0 MB", items[1].fileSize)
    }

    @Test
    fun emptyTypedListYieldsNoItems() {
        assertEquals(0, movieListItemsFromMovies(emptyList()).size)
    }
}
