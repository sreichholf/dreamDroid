package net.reichholf.dreamdroid.ui.services

import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import org.junit.Assert.assertEquals
import org.junit.Test

class HubMovieListSessionTest {
    @Test
    fun staleReloadDoesNotReplaceNewerMovieList() {
        val session = HubMovieListSession()
        val state = MovieListState()
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        session.listState = state
        session.refresh = ComposeRefreshState()

        val staleGeneration = session.beginLoad()
        val freshGeneration = session.beginLoad()
        session.applyLoadResult(
            generation = staleGeneration,
            success = true,
            next = listOf(Movie(title = "Stale folder", serviceName = "ZDF")),
            errorText = null,
        )
        assertEquals(emptyList<String>(), state.items.map { it.title })

        session.applyLoadResult(
            generation = freshGeneration,
            success = true,
            next = listOf(Movie(title = "Fresh folder", serviceName = "ARD")),
            errorText = null,
        )
        assertEquals(listOf("Fresh folder"), state.items.map { it.title })

        session.applyLoadResult(
            generation = staleGeneration,
            success = false,
            next = emptyList(),
            errorText = "timeout",
        )
        assertEquals(listOf("Fresh folder"), state.items.map { it.title })
    }
}
