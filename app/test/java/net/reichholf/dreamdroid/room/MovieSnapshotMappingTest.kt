package net.reichholf.dreamdroid.room

import net.reichholf.dreamdroid.enigma.Movie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MovieSnapshotMappingTest {
    @Test
    fun roundTripPreservesMovieFields() {
        val movie = Movie(
            reference = "1:0:0:0:0:0:0:0:0:0:/media/hdd/movie/news.ts",
            title = "Evening News",
            description = "Nightly",
            descriptionExtended = "Full bulletin.",
            serviceName = "Das Erste HD",
            time = "1893456000",
            timeReadable = "1 Jan 2030 12:00",
            length = "45",
            tags = "news sports",
            fileName = "/media/hdd/movie/news.ts",
            fileSize = "104857600",
            fileSizeReadable = "100 MB"
        )
        assertEquals(movie, movie.toListEntity(7, "/media/hdd/movie", 2).toMovie())
        val entity = movie.toListEntity(7, "/media/hdd/movie", 2)
        assertEquals(7, entity.profileId)
        assertEquals("/media/hdd/movie", entity.dirname)
        assertEquals(2, entity.position)
    }
}
