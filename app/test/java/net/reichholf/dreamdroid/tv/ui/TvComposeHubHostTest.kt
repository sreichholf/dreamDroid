package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.tv.BrowseItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvComposeHubHostTest {
    @Test
    fun timersHeaderIdIsTimers() {
        assertEquals("timers", TvComposeHubHost.HEADER_TIMERS_ID)
    }

    @Test
    fun savedReloadFlagReloadsOnce() {
        val handle = SavedStateHandle()
        assertFalse(consumeTvHubReload(handle))
        markTvHubReload(handle)
        assertTrue(consumeTvHubReload(handle))
        assertFalse(consumeTvHubReload(handle))
    }

    @Test
    fun unmarkedHubDoesNotReload() {
        assertFalse(consumeTvHubReload(SavedStateHandle()))
        markTvHubReload(null)
    }

    @Test
    fun defaultSettingsKindsAreReloadPreferencesProfile() {
        assertEquals(
            listOf(
                BrowseItem.Kind.Reload,
                BrowseItem.Kind.Preferences,
                BrowseItem.Kind.Profile
            ),
            TvComposeHubHost.defaultSettingsKinds()
        )
    }

    @Test
    fun drawerListsBouquetsMultiEpgMoviesTimersThenPreferences() {
        val headers = hubNavHeaders(
            bouquetRows = listOf(row(FAVOURITES, "Favourites"), row(SPORTS, "")),
            movieLocations = listOf("/media/hdd/movie/")
        )
        assertEquals(
            listOf(
                HubNavHeader(FAVOURITES, "Favourites"),
                HubNavHeader(SPORTS, "Services"),
                HubNavHeader(TvComposeHubHost.HEADER_MULTIEPG_ID, "MultiEPG"),
                HubNavHeader(
                    TvComposeHubHost.movieHeaderId("/media/hdd/movie/"),
                    "/media/hdd/movie/"
                ),
                HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timers"),
                HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences")
            ),
            headers
        )
    }

    @Test
    fun drawerWithoutBouquetsShowsPlaceholderAndNoMultiEpg() {
        assertEquals(
            listOf(
                HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services"),
                HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timers"),
                HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences")
            ),
            hubNavHeaders(bouquetRows = emptyList(), movieLocations = emptyList())
        )
    }

    @Test
    fun selectionSurvivesReloadOnlyWhileItsHeaderIsInTheDrawer() {
        val rows = listOf(row(FAVOURITES, "Favourites"))
        val movie = TvComposeHubHost.movieHeaderId("/media/hdd/movie/")
        for (id in listOf(TvComposeHubHost.HEADER_SETTINGS_ID, TvComposeHubHost.HEADER_TIMERS_ID)) {
            assertTrue(TvComposeHubHost.hubHeaderSurvivesReload(id, emptyList(), emptyList()))
        }
        assertTrue(
            TvComposeHubHost.hubHeaderSurvivesReload(
                TvComposeHubHost.HEADER_MULTIEPG_ID,
                rows,
                emptyList()
            )
        )
        assertFalse(
            TvComposeHubHost.hubHeaderSurvivesReload(
                TvComposeHubHost.HEADER_MULTIEPG_ID,
                emptyList(),
                emptyList()
            )
        )
        assertTrue(TvComposeHubHost.hubHeaderSurvivesReload(FAVOURITES, rows, emptyList()))
        assertFalse(TvComposeHubHost.hubHeaderSurvivesReload(SPORTS, rows, emptyList()))
        assertTrue(
            TvComposeHubHost.hubHeaderSurvivesReload(movie, rows, listOf("/media/hdd/movie/"))
        )
        assertFalse(TvComposeHubHost.hubHeaderSurvivesReload(movie, rows, emptyList()))
        assertTrue(
            TvComposeHubHost.hubHeaderSurvivesReload(
                TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                emptyList(),
                emptyList()
            )
        )
        assertFalse(
            TvComposeHubHost.hubHeaderSurvivesReload(
                TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                rows,
                emptyList()
            )
        )
    }

    @Test
    fun hubOpensOnTheFirstBouquetElseThePlaceholder() {
        assertEquals(
            FAVOURITES,
            TvComposeHubHost.firstHubHeader(
                listOf(row(FAVOURITES, "Favourites"), row(SPORTS, "Sports"))
            )
        )
        assertEquals(
            TvComposeHubHost.HEADER_PLACEHOLDER_ID,
            TvComposeHubHost.firstHubHeader(emptyList())
        )
    }

    @Test
    fun multiEpgRouteKeepsNonBlankBouquetFields() {
        val ref = "1:7:1:0:0:0:0:0:0:0:Favourites"
        assertEquals(
            TvMultiEpg(bouquetRef = ref, bouquetName = "Favourites"),
            TvComposeHubHost.tvMultiEpgRoute(ref, "Favourites")
        )
        assertEquals(TvMultiEpg(), TvComposeHubHost.tvMultiEpgRoute())
        assertEquals(TvMultiEpg(), TvComposeHubHost.tvMultiEpgRoute("  ", ""))
        assertEquals(
            TvMultiEpg(bouquetRef = ref),
            TvComposeHubHost.tvMultiEpgRoute(ref, "  ")
        )
    }

    @Test
    fun hubHeaderIconResMatchesDrawerRole() {
        assertEquals(
            R.drawable.ic_badge_settings,
            TvComposeHubHost.hubHeaderIconRes(TvComposeHubHost.HEADER_SETTINGS_ID)
        )
        assertEquals(
            R.drawable.ic_menu_timer,
            TvComposeHubHost.hubHeaderIconRes(TvComposeHubHost.HEADER_TIMERS_ID)
        )
        assertEquals(
            R.drawable.ic_multiepg,
            TvComposeHubHost.hubHeaderIconRes(TvComposeHubHost.HEADER_MULTIEPG_ID)
        )
        assertEquals(
            R.drawable.ic_menu_tv,
            TvComposeHubHost.hubHeaderIconRes(TvComposeHubHost.HEADER_PLACEHOLDER_ID)
        )
        assertEquals(
            R.drawable.ic_menu_tv,
            TvComposeHubHost.hubHeaderIconRes("1:7:1:0:0:0:0:0:0:0:Favourites")
        )
        assertEquals(
            R.drawable.ic_menu_movie,
            TvComposeHubHost.hubHeaderIconRes(TvComposeHubHost.movieHeaderId("/hdd/movie"))
        )
    }

    @Test
    fun settingsBadgesMatchLiveTvCards() {
        assertEquals(
            R.drawable.ic_badge_reload,
            TvComposeHubHost.settingsBadgeRes(BrowseItem.Kind.Reload)
        )
        assertEquals(
            R.drawable.ic_badge_settings,
            TvComposeHubHost.settingsBadgeRes(BrowseItem.Kind.Preferences)
        )
        assertEquals(
            R.drawable.ic_badge_profiles,
            TvComposeHubHost.settingsBadgeRes(BrowseItem.Kind.Profile)
        )
    }

    @Test
    fun browseErrorHiddenOnSettingsHeader() {
        assertFalse(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_SETTINGS_ID,
                loading = false,
                errorText = "box offline"
            )
        )
    }

    @Test
    fun browseErrorHiddenOnTimersHeader() {
        assertFalse(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_TIMERS_ID,
                loading = false,
                errorText = "box offline"
            )
        )
    }

    @Test
    fun browseErrorShownOnContentRows() {
        assertTrue(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                loading = false,
                errorText = "box offline"
            )
        )
        assertFalse(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                loading = true,
                errorText = "box offline"
            )
        )
        assertFalse(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                loading = false,
                errorText = null
            )
        )
        assertFalse(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = true
            )
        )
    }

    @Test
    fun browseErrorShownOnEmptyMultiEpgPane() {
        assertTrue(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_MULTIEPG_ID,
                loading = false,
                errorText = "box offline"
            )
        )
    }

    @Test
    fun browseErrorHiddenOnMultiEpgWhenBouquetCardsExist() {
        assertFalse(
            TvComposeHubHost.shouldShowBrowseError(
                TvComposeHubHost.HEADER_MULTIEPG_ID,
                loading = false,
                errorText = "box offline",
                hasPaintedContent = true
            )
        )
    }

    private fun hubNavHeaders(bouquetRows: List<HubBouquetRow>, movieLocations: List<String>) =
        TvComposeHubHost.hubNavHeaders(
            bouquetRows = bouquetRows,
            movieLocations = movieLocations,
            placeholderTitle = "Services",
            multiEpgTitle = "MultiEPG",
            timersTitle = "Timers",
            settingsTitle = "Preferences"
        )

    private fun row(reference: String, name: String) =
        HubBouquetRow(bouquet = Service(reference, name), services = emptyList())

    private companion object {
        const val FAVOURITES = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\""
        const val SPORTS = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\""
    }
}
