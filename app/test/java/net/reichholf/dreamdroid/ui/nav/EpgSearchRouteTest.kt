package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpgSearchRouteTest {
    @Test
    fun navigateToEpgSearchRoundTripsSlashQuestionAndSpace() {
        val queries = listOf(
            "Tagesschau / Wetter",
            "who? what",
            "two words",
            "Tagesschau / Wetter? live"
        )
        for (query in queries) {
            val route = PhoneNavRoutes.epgSearchRoute(query)
            val path = route.substringBefore('?')
            assertEquals("epg_search", path)
            assertFalse(
                path.contains("/"),
                "path must not contain extra segments from the query: $query"
            )
            assertEquals(query, PhoneNavRoutes.queryFromEpgSearchRoute(route))
        }
    }

    @Test
    fun slashStaysInQueryParamNotPath() {
        val route = PhoneNavRoutes.epgSearchRoute("Tagesschau / Wetter")
        assertTrue(route.contains("%2F") || route.contains("%2f"))
        assertEquals("epg_search", route.substringBefore('?'))
        assertEquals("Tagesschau / Wetter", PhoneNavRoutes.queryFromEpgSearchRoute(route))
    }

    @Test
    fun questionMarkDoesNotSplitTheQueryParam() {
        val route = PhoneNavRoutes.epgSearchRoute("who? what")
        assertEquals(1, route.count { it == '?' })
        assertEquals("who? what", PhoneNavRoutes.queryFromEpgSearchRoute(route))
    }

    @Test
    fun graphPatternStillStartsWithEpgSearch() {
        assertTrue(PhoneNavRoutes.EPG_SEARCH.startsWith("epg_search"))
    }

    @Test
    fun emptyQueryKeepsTheSearchRoute() {
        val route = PhoneNavRoutes.epgSearchRoute("")
        assertEquals("epg_search", route.substringBefore('?'))
        assertEquals("", PhoneNavRoutes.queryFromEpgSearchRoute(route))
    }
}
