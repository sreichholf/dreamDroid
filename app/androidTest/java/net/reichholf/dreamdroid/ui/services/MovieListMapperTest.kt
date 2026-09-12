package net.reichholf.dreamdroid.ui.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.Movie
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
    fun toExtendedHashMapKeepsLegacyKeys() {
        val movie = Movie(
            "ref",
            "Title",
            "desc",
            "ext",
            "Service",
            "100",
            "readable",
            "30",
            "tag",
            "/file.ts",
            "1024",
            "0 MB",
        )
        val map = movieToExtendedHashMap(movie)
        assertEquals("Title", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_TITLE))
        assertEquals("ref", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_REFERENCE))
        assertEquals("/file.ts", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_NAME))
        assertEquals("0 MB", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_SIZE_READABLE))
    }

    @Test
    fun fromExtendedHashMapRoundTripsFields() {
        val movie = Movie(
            "ref",
            "Title",
            "desc",
            "ext",
            "Service",
            "100",
            "readable",
            "30",
            "tag",
            "/file.ts",
            "1024",
            "0 MB",
        )
        val map = movieToExtendedHashMap(movie)
        val back = movieFromExtendedHashMap(map)
        assertEquals(movie.reference, back.reference)
        assertEquals(movie.title, back.title)
        assertEquals(movie.description, back.description)
        assertEquals(movie.descriptionExtended, back.descriptionExtended)
        assertEquals(movie.serviceName, back.serviceName)
        assertEquals(movie.time, back.time)
        assertEquals(movie.timeReadable, back.timeReadable)
        assertEquals(movie.length, back.length)
        assertEquals(movie.tags, back.tags)
        assertEquals(movie.fileName, back.fileName)
        assertEquals(movie.fileSize, back.fileSize)
        assertEquals(movie.fileSizeReadable, back.fileSizeReadable)
    }

    @Test
    fun emptyTypedListYieldsNoItems() {
        assertEquals(0, movieListItemsFromMovies(emptyList()).size)
    }
}
