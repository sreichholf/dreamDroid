package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.tv.BrowseItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
    fun destinationForSettingsKinds() {
        assertEquals(TvSettings, TvComposeHubHost.destinationForKind(BrowseItem.Kind.Preferences))
        assertEquals(TvProfiles, TvComposeHubHost.destinationForKind(BrowseItem.Kind.Profile))
        assertNull(TvComposeHubHost.destinationForKind(BrowseItem.Kind.Reload))
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
    fun settingsTimersAndMultiEpgArePersistentBrowsePanes() {
        assertTrue(TvComposeHubHost.isPersistentHubHeader(TvComposeHubHost.HEADER_SETTINGS_ID))
        assertTrue(TvComposeHubHost.isPersistentHubHeader(TvComposeHubHost.HEADER_TIMERS_ID))
        assertTrue(TvComposeHubHost.isPersistentHubHeader(TvComposeHubHost.HEADER_MULTIEPG_ID))
        assertFalse(
            TvComposeHubHost.isPersistentHubHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID)
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
}
