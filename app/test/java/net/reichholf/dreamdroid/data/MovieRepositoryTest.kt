package net.reichholf.dreamdroid.data

import kotlinx.coroutines.test.runTest
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.testutil.MovieTestReceiver
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.HDD
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.USB
import net.reichholf.dreamdroid.testutil.MovieTestReceiver.Companion.movieList
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [MovieRepository] over a [MockWebServer][okhttp3.mockwebserver.MockWebServer] and Room. */
class MovieRepositoryTest {
    private val receiver = MovieTestReceiver()
    private val repository = receiver.movies

    @BeforeEach
    fun setUp() {
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        receiver.stop()
    }

    @Test
    fun unfilteredLoadWritesSnapshotOfThatLocation() = runTest {
        val load = repository.movies(HDD, emptyList())

        assertEquals(
            MovieListLoad.Movies(repository.cachedMovies(HDD)!!, cached = false),
            load
        )
        assertEquals(listOf("Evening News", "Empty Size"), titles(load))
        assertNull(repository.cachedMovies(USB))
        val request = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/movielist", request.encodedPath)
        assertEquals(HDD, request.queryParameter("dirname"))
        assertNull(request.queryParameter("tag"))
    }

    @Test
    fun snapshotKeepsMovieFields() = runTest {
        repository.movies(HDD, emptyList())

        val cached = repository.cachedMovies(HDD)!!.first()
        assertEquals("Das Erste HD", cached.serviceName)
        assertEquals("/media/hdd/movie/news.ts", cached.fileName)
        assertEquals("104857600", cached.fileSize)
    }

    @Test
    fun defaultLocationSendsNoDirname() = runTest {
        repository.movies("", emptyList())

        assertNull(receiver.server.takeRequest().requestUrl!!.queryParameter("dirname"))
    }

    @Test
    fun filteredLoadSendsTagsAndWritesNothing() = runTest {
        val load = repository.movies(HDD, listOf("news", "sports"))

        assertEquals(listOf("Evening News", "Empty Size"), titles(load))
        assertEquals(
            "news sports",
            receiver.server.takeRequest().requestUrl!!.queryParameter("tag")
        )
        assertNull(repository.cachedMovies(HDD))
    }

    @Test
    fun failureFallsBackToSnapshotWithoutRewriting() = runTest {
        repository.saveMovies(HDD, listOf(Movie(title = "News"), Movie(title = "Sport")))
        receiver.answer = { MockResponse().setResponseCode(500) }

        val load = repository.movies(HDD, emptyList())

        assertEquals(
            MovieListLoad.Movies(listOf(Movie(title = "News"), Movie(title = "Sport")), true),
            load
        )
        assertEquals(listOf("News", "Sport"), repository.cachedMovies(HDD)?.map { it.title })
    }

    @Test
    fun failureWithoutSnapshotFails() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val load = repository.movies(HDD, emptyList())

        assertInstanceOf(EnigmaFailure.Http::class.java, failure(load))
        assertNull(repository.cachedMovies(HDD))
    }

    @Test
    fun filteredFailureDoesNotPaintUnfilteredSnapshot() = runTest {
        repository.saveMovies(HDD, listOf(Movie(title = "News")))
        receiver.answer = { MockResponse().setResponseCode(500) }

        val load = repository.movies(HDD, listOf("sports"))

        assertInstanceOf(EnigmaFailure.Http::class.java, failure(load))
        assertEquals(listOf("News"), repository.cachedMovies(HDD)?.map { it.title })
    }

    @Test
    fun writtenEmptyIsEmptyNotMissing() = runTest {
        receiver.answer = { movieList() }
        assertEquals(MovieListLoad.Movies(emptyList(), cached = false), load(HDD))
        assertEquals(emptyList<Movie>(), repository.cachedMovies(HDD))

        receiver.answer = { MockResponse().setResponseCode(500) }
        assertEquals(MovieListLoad.Movies(emptyList(), cached = true), load(HDD))
        assertInstanceOf(MovieListLoad.Failed::class.java, load(USB))
    }

    @Test
    fun loadReplacesOnlyItsLocation() = runTest {
        repository.saveMovies(HDD, listOf(Movie(title = "Old"), Movie(title = "Drop")))
        repository.saveMovies(USB, listOf(Movie(title = "Keep")))
        receiver.answer = { movieList("New") }

        repository.movies(HDD, emptyList())

        assertEquals(listOf("New"), repository.cachedMovies(HDD)?.map { it.title })
        assertEquals(listOf("Keep"), repository.cachedMovies(USB)?.map { it.title })
        assertEquals(1, receiver.profiles.database.movieDao().getMovieList(PROFILE_ID, HDD).size)
    }

    @Test
    fun snapshotsBelongToTheirProfile() = runTest {
        repository.saveMovies(HDD, listOf(Movie(title = "News")))
        repository.saveLocations(listOf(HDD))

        receiver.useProfile(PROFILE_ID + 1)

        assertNull(repository.cachedMovies(HDD))
        assertNull(repository.cachedLocations())
    }

    @Test
    fun answeredLocationsReplaceTheStrip() = runTest {
        repository.saveLocations(listOf("/old", "/drop"))
        val live = listOf(HDD, USB)

        assertEquals(live, repository.locationsOrCached(receiverAnswered = true, live = live))

        assertEquals(live, repository.cachedLocations())
    }

    @Test
    fun unansweredLocationsPaintTheStripWithoutRewriting() = runTest {
        val cached = listOf(HDD, USB)
        repository.saveLocations(cached)

        val painted = repository.locationsOrCached(receiverAnswered = false, live = listOf("/x"))

        assertEquals(cached, painted)
        assertEquals(cached, repository.cachedLocations())
    }

    @Test
    fun unansweredLocationsWithoutStripKeepLiveAndWriteNothing() = runTest {
        val painted = repository.locationsOrCached(receiverAnswered = false, live = listOf("/x"))

        assertEquals(listOf("/x"), painted)
        assertNull(repository.cachedLocations())
    }

    @Test
    fun writtenEmptyStripIsEmptyNotMissing() = runTest {
        repository.saveLocations(emptyList())

        assertEquals(emptyList<String>(), repository.cachedLocations())
    }

    @Test
    fun locationStripCountsAsUseDrivenCache() = runTest {
        val dao = receiver.profiles.database.movieDao()
        assertFalse(hasUseDrivenCache(emptyList(), dao.locationMetaCount(PROFILE_ID) > 0))

        repository.saveLocations(listOf(HDD))

        assertTrue(hasUseDrivenCache(emptyList(), dao.locationMetaCount(PROFILE_ID) > 0))
        assertFalse(hasUseDrivenCache(emptyList(), dao.locationMetaCount(PROFILE_ID + 1) > 0))
    }

    @Test
    fun deleteSendsTheReference() = runTest {
        val response = repository.delete(Movie(reference = "1:0:0:0:0:0:0:0:0:0:/hdd/a.ts"))

        assertEquals("True", response.value?.state)
        val request = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/moviedelete", request.encodedPath)
        assertEquals("1:0:0:0:0:0:0:0:0:0:/hdd/a.ts", request.queryParameter("sRef"))
    }

    @Test
    fun directLinkOnlyWithoutLogin() {
        val link = repository.directLink("/media/hdd/movie/news.ts")!!

        assertTrue(link.startsWith("http://${receiver.server.hostName}:${receiver.server.port}"))
        assertTrue(link.contains("/file?file="))

        receiver.useProfile(PROFILE_ID, login = true)
        assertNull(repository.directLink("/media/hdd/movie/news.ts"))
    }

    @Test
    fun downloadWritesTheRecordingIntoTheCache() = runTest {
        receiver.useProfile(PROFILE_ID, login = true)
        receiver.answer = { MockResponse().setBody("recording") }

        val download = repository.downloadToCache("/media/hdd/movie/news.ts")

        val file = (download as MovieDownload.Ready).file
        assertEquals("news.ts", file.name)
        assertEquals(receiver.profiles.context.cacheDir, file.parentFile)
        assertEquals("recording", file.readText())
        val request = receiver.server.takeRequest()
        assertEquals("/file", request.requestUrl!!.encodedPath)
        assertEquals("/media/hdd/movie/news.ts", request.requestUrl!!.queryParameter("file"))
        assertTrue(request.getHeader("Authorization").orEmpty().startsWith("Basic "))
    }

    @Test
    fun failedDownloadLeavesNoFile() = runTest {
        receiver.answer = { MockResponse().setResponseCode(404) }

        val download = repository.downloadToCache("/media/hdd/movie/news.ts")

        assertInstanceOf(MovieDownload.Failed::class.java, download)
        assertFalse(receiver.profiles.context.cacheDir.resolve("news.ts").exists())
    }

    private suspend fun load(location: String): MovieListLoad =
        repository.movies(location, emptyList())

    private fun titles(load: MovieListLoad): List<String> =
        (load as MovieListLoad.Movies).movies.map { it.title }

    private fun failure(load: MovieListLoad): EnigmaFailure? =
        (load as MovieListLoad.Failed).error?.failure
}
