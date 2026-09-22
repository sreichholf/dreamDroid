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
            controller,
            shellNav = null,
            shellRail = null
        )
        assertTrue(controller.content is ShellDestinationBarContent.Hidden)
    }

    @Test
    fun hubRouteLeavesPublishedChrome() {
        val controller = ShellDestinationBarController()
        controller.content = ShellDestinationBarContent.TvMovies(TvMoviesHubState())
        applyShellDestinationBarForRoute(
            PhoneNavRoutes.HUB,
            controller,
            shellNav = null,
            shellRail = null
        )
        assertTrue(controller.content is ShellDestinationBarContent.TvMovies)
    }
}
