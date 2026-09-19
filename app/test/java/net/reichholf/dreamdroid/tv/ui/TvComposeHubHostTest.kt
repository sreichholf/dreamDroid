package net.reichholf.dreamdroid.tv.ui

import android.app.Activity
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.tv.activities.PreferenceActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvComposeHubHostTest {
    @Test
    fun resultOkReloadsHubBrowseData() {
        var reloads = 0
        TvComposeHubHost.applyPreferenceActivityResult(Activity.RESULT_OK) {
            reloads++
        }
        assertEquals(1, reloads)
    }

    @Test
    fun canceledPreferenceResultDoesNotReload() {
        var reloads = 0
        TvComposeHubHost.applyPreferenceActivityResult(Activity.RESULT_CANCELED) {
            reloads++
        }
        assertEquals(0, reloads)
    }

    @Test
    fun preferenceTypeForSettingsKinds() {
        assertEquals(
            PreferenceActivity.PREFS_TYPE_GENERIC,
            TvComposeHubHost.preferenceTypeForKind(BrowseItem.Kind.Preferences)
        )
        assertEquals(
            PreferenceActivity.PREFS_TYPE_PROFILE,
            TvComposeHubHost.preferenceTypeForKind(BrowseItem.Kind.Profile)
        )
        assertNull(TvComposeHubHost.preferenceTypeForKind(BrowseItem.Kind.Reload))
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
    fun multiEpgIsALaunchHeaderNotABrowsePane() {
        assertTrue(TvComposeHubHost.isLaunchHeader(TvComposeHubHost.HEADER_MULTIEPG_ID))
        assertFalse(TvComposeHubHost.isLaunchHeader(TvComposeHubHost.HEADER_SETTINGS_ID))
        assertTrue(TvComposeHubHost.isPersistentHubHeader(TvComposeHubHost.HEADER_SETTINGS_ID))
        assertFalse(TvComposeHubHost.isPersistentHubHeader(TvComposeHubHost.HEADER_MULTIEPG_ID))
        assertFalse(
            TvComposeHubHost.isPersistentHubHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID)
        )
    }

    @Test
    fun hubHeaderIconResMatchesDrawerRole() {
        assertEquals(
            R.drawable.ic_badge_settings,
            TvComposeHubHost.hubHeaderIconRes(TvComposeHubHost.HEADER_SETTINGS_ID)
        )
        assertEquals(
            R.drawable.ic_multiepg_clock,
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
}
