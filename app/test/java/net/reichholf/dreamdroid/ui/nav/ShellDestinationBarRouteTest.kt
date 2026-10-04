package net.reichholf.dreamdroid.ui.nav

import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellDestinationBarRouteTest {
    @Test
    fun timerEditClearsShellChromeImmediately() {
        val controller = ShellDestinationBarController()
        controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
        applyShellDestinationBarForRoute(
            PhoneNavRoutes.TIMER_EDIT,
            floating = false,
            controller
        )
        assertTrue(controller.content is ShellDestinationBarContent.Hidden)
    }

    @Test
    fun hubRouteLeavesPublishedChrome() {
        val controller = ShellDestinationBarController()
        controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
        applyShellDestinationBarForRoute(
            PhoneNavRoutes.HUB,
            floating = false,
            controller
        )
        assertTrue(controller.content is ShellDestinationBarContent.TvMovies)
    }

    @Test
    fun dialogOverHubLeavesPublishedChrome() {
        val controller = ShellDestinationBarController()
        controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
        applyShellDestinationBarForRoute(
            PhoneNavRoutes.CHANGELOG,
            floating = true,
            controller
        )
        assertTrue(controller.content is ShellDestinationBarContent.TvMovies)
    }
}
