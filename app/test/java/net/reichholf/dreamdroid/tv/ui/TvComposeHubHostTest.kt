package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.tv.BrowseItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvComposeHubHostTest {
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
}
