package net.reichholf.dreamdroid.room

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.MovieListLoadResult
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.services.HubMovieListSession
import net.reichholf.dreamdroid.ui.services.MovieListState
import net.reichholf.dreamdroid.ui.services.movieLocationsAfterHttpOrCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieSnapshotStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MovieDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        dao = db.movieDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun writeAndReadLocations() = runBlocking {
        val locations = listOf("/media/hdd/movie", "/media/usb/movie")
        MovieSnapshotStore.replaceLocations(dao, PROFILE, locations)
        assertEquals(locations, MovieSnapshotStore.loadLocations(dao, PROFILE))
        assertNull(MovieSnapshotStore.loadLocations(dao, OTHER_PROFILE))
    }

    @Test
    fun writeAndReadMoviesForOneDirname() = runBlocking {
        val movies = listOf(sampleMovie(title = "News"), sampleMovie(title = "Sport"))
        MovieSnapshotStore.replaceMovies(dao, PROFILE, HDD, movies)
        val loaded = MovieSnapshotStore.loadMovies(dao, PROFILE, HDD)
        assertEquals(listOf("News", "Sport"), loaded?.map { it.title })
        assertEquals("Das Erste HD", loaded!![0].serviceName)
        assertEquals("/media/hdd/movie/news.ts", loaded[0].fileName)
        assertEquals("104857600", loaded[0].fileSize)
        assertNull(MovieSnapshotStore.loadMovies(dao, PROFILE, USB))
        assertNull(MovieSnapshotStore.loadMovies(dao, OTHER_PROFILE, HDD))
    }

    @Test
    fun writtenEmptyIsEmptyListNotMissing() = runBlocking {
        MovieSnapshotStore.replaceLocations(dao, PROFILE, emptyList())
        assertEquals(emptyList<String>(), MovieSnapshotStore.loadLocations(dao, PROFILE))
        assertNull(MovieSnapshotStore.loadLocations(dao, OTHER_PROFILE))

        MovieSnapshotStore.replaceMovies(dao, PROFILE, HDD, emptyList())
        assertEquals(emptyList<Movie>(), MovieSnapshotStore.loadMovies(dao, PROFILE, HDD))
        assertNull(MovieSnapshotStore.loadMovies(dao, PROFILE, USB))
    }

    @Test
    fun replaceOnWriteReplacesRows() = runBlocking {
        MovieSnapshotStore.replaceLocations(
            dao,
            PROFILE,
            listOf("/old", "/drop")
        )
        MovieSnapshotStore.replaceLocations(dao, PROFILE, listOf("/media/hdd/movie"))
        assertEquals(
            listOf("/media/hdd/movie"),
            MovieSnapshotStore.loadLocations(dao, PROFILE)
        )

        MovieSnapshotStore.replaceMovies(
            dao,
            PROFILE,
            HDD,
            listOf(sampleMovie(title = "Old"), sampleMovie(title = "Drop"))
        )
        MovieSnapshotStore.replaceMovies(dao, PROFILE, USB, listOf(sampleMovie(title = "Keep")))
        MovieSnapshotStore.replaceMovies(dao, PROFILE, HDD, listOf(sampleMovie(title = "New")))
        assertEquals(
            listOf("New"),
            MovieSnapshotStore.loadMovies(dao, PROFILE, HDD)?.map { it.title }
        )
        assertEquals(
            listOf("Keep"),
            MovieSnapshotStore.loadMovies(dao, PROFILE, USB)?.map { it.title }
        )
        assertEquals(1, dao.getMovieList(PROFILE, HDD).size)
    }

    @Test
    fun successfulHttpWritesSnapshot() = runBlocking {
        val session = movieSession(this, HDD)
        session.loadMovies = { _, _ ->
            MovieListLoadResult(true, listOf(sampleMovie(title = "News")), null)
        }
        session.loadAndApply(session.beginLoad())
        val loaded = MovieSnapshotStore.loadMovies(dao, PROFILE, HDD)
        assertEquals(listOf("News"), loaded?.map { it.title })
        assertEquals(listOf("News"), session.listState!!.items.map { it.title })
        assertNull(MovieSnapshotStore.loadMovies(dao, PROFILE, USB))
    }

    @Test
    fun successfulHttpWithTagsDoesNotWriteSnapshot() = runBlocking {
        val session = movieSession(this, HDD)
        session.selectedTags = arrayListOf("news")
        session.loadMovies = { _, _ ->
            MovieListLoadResult(true, listOf(sampleMovie(title = "News")), null)
        }
        session.loadAndApply(session.beginLoad())
        assertNull(MovieSnapshotStore.loadMovies(dao, PROFILE, HDD))
        assertEquals(listOf("News"), session.listState!!.items.map { it.title })
    }

    @Test
    fun failedHttpDoesNotWriteSnapshot() = runBlocking {
        val session = movieSession(this, HDD)
        var emptyMessage: String? = null
        session.onEmptyMessage = { emptyMessage = it }
        session.loadMovies = { _, _ ->
            MovieListLoadResult(false, listOf(sampleMovie(title = "Ghost")), "timeout")
        }
        session.loadAndApply(session.beginLoad())
        assertNull(MovieSnapshotStore.loadMovies(dao, PROFILE, HDD))
        assertEquals(0, dao.movieListMetaCount(PROFILE, HDD))
        assertEquals("timeout", emptyMessage)
        assertEquals(emptyList<String>(), session.listState!!.items.map { it.title })
    }

    @Test
    fun failedHttpPaintsSnapshotWithoutRewriting() = runBlocking {
        val original = listOf(sampleMovie(title = "News"), sampleMovie(title = "Sport"))
        MovieSnapshotStore.replaceMovies(dao, PROFILE, HDD, original)
        val session = movieSession(this, HDD)
        var emptyMessage: String? = "stale"
        session.onEmptyMessage = { emptyMessage = it }
        session.loadMovies = { _, _ ->
            MovieListLoadResult(false, emptyList(), "timeout")
        }
        session.loadAndApply(session.beginLoad())
        val loaded = MovieSnapshotStore.loadMovies(dao, PROFILE, HDD)
        assertEquals(listOf("News", "Sport"), loaded?.map { it.title })
        assertEquals(listOf("News", "Sport"), session.listState!!.items.map { it.title })
        assertNull(emptyMessage)
    }

    @Test
    fun failedHttpOnWrittenEmptyKeepsNoListItemCopy() = runBlocking {
        MovieSnapshotStore.replaceMovies(dao, PROFILE, HDD, emptyList())
        val session = movieSession(this, HDD)
        var emptyMessage: String? = null
        session.onEmptyMessage = { emptyMessage = it }
        session.loadMovies = { _, _ ->
            MovieListLoadResult(false, emptyList(), "timeout")
        }
        session.loadAndApply(session.beginLoad())
        assertEquals(emptyList<Movie>(), MovieSnapshotStore.loadMovies(dao, PROFILE, HDD))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(context.getString(R.string.no_list_item), emptyMessage)
        assertEquals(emptyList<String>(), session.listState!!.items.map { it.title })
    }

    @Test
    fun successfulLocationsHttpWritesStrip() = runBlocking {
        val live = listOf("/media/hdd/movie", "/media/usb/movie")
        val painted = movieLocationsAfterHttpOrCache(dao, PROFILE, true, live)
        assertEquals(live, painted)
        assertEquals(live, MovieSnapshotStore.loadLocations(dao, PROFILE))
        assertNull(MovieSnapshotStore.loadLocations(dao, OTHER_PROFILE))
    }

    @Test
    fun failedLocationsHttpDoesNotWriteStrip() = runBlocking {
        val painted = movieLocationsAfterHttpOrCache(
            dao,
            PROFILE,
            false,
            listOf("/hdd/movie")
        )
        assertEquals(listOf("/hdd/movie"), painted)
        assertNull(MovieSnapshotStore.loadLocations(dao, PROFILE))
        assertEquals(0, dao.locationMetaCount(PROFILE))
    }

    @Test
    fun failedLocationsHttpPaintsStripWithoutRewriting() = runBlocking {
        val cached = listOf("/media/hdd/movie", "/media/usb/movie")
        MovieSnapshotStore.replaceLocations(dao, PROFILE, cached)
        val painted = movieLocationsAfterHttpOrCache(
            dao,
            PROFILE,
            false,
            listOf("/hdd/movie")
        )
        assertEquals(cached, painted)
        assertEquals(cached, MovieSnapshotStore.loadLocations(dao, PROFILE))
    }

    private fun movieSession(
        scope: kotlinx.coroutines.CoroutineScope,
        dirname: String
    ): HubMovieListSession {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = HubMovieListSession()
        session.context = context
        session.listState = MovieListState()
        session.refresh = ComposeRefreshState()
        session.scope = scope
        session.movieDao = dao
        session.profileId = PROFILE
        session.location = dirname
        return session
    }

    private fun sampleMovie(title: String): Movie = Movie(
        reference = "1:0:0:0:0:0:0:0:0:0:/media/hdd/movie/news.ts",
        title = title,
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

    companion object {
        private const val PROFILE = 7
        private const val OTHER_PROFILE = 8
        private const val HDD = "/media/hdd/movie"
        private const val USB = "/media/usb/movie"
    }
}
