package net.reichholf.dreamdroid.ui.profiles

import android.content.Intent
import net.reichholf.dreamdroid.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ProfileEditBindTest {
    @Test
    fun firstBindRestoresSavedTagEvenWhenEpochDiffers() {
        val bind = profileEditBind(
            hasBound = false,
            boundTag = "",
            boundEpoch = 0,
            routeTag = "profile_edit:4",
            remountEpoch = 0,
            savedTag = "profile_edit:4"
        )
        assertEquals(ProfileEditBind.RestoreSaved, bind)
    }

    @Test
    fun firstBindWithoutSavedTagLoadsLaunchProfile() {
        val bind = profileEditBind(
            hasBound = false,
            boundTag = "",
            boundEpoch = 0,
            routeTag = "profile_edit:new",
            remountEpoch = 0,
            savedTag = null
        )
        assertEquals(ProfileEditBind.LoadLaunch, bind)
    }

    @Test
    fun differentSavedTagLoadsLaunchProfile() {
        val bind = profileEditBind(
            hasBound = false,
            boundTag = "",
            boundEpoch = 0,
            routeTag = "profile_edit:9",
            remountEpoch = 0,
            savedTag = "profile_edit:4"
        )
        assertEquals(ProfileEditBind.LoadLaunch, bind)
    }

    @Test
    fun sameTagAndEpochKeepsWorkingCopy() {
        val bind = profileEditBind(
            hasBound = true,
            boundTag = "profile_edit:4",
            boundEpoch = 1,
            routeTag = "profile_edit:4",
            remountEpoch = 1,
            savedTag = "profile_edit:4"
        )
        assertEquals(ProfileEditBind.Keep, bind)
    }

    @Test
    fun remountEpochLoadsLaunchProfile() {
        val bind = profileEditBind(
            hasBound = true,
            boundTag = "profile_edit:4",
            boundEpoch = 1,
            routeTag = "profile_edit:4",
            remountEpoch = 2,
            savedTag = "profile_edit:4"
        )
        assertEquals(ProfileEditBind.LoadLaunch, bind)
    }

    @Test
    fun editActionUsesLaunchProfile() {
        val profile = Profile()
        profile.name = "Living room"
        assertSame(profile, initialProfileForEdit(Intent.ACTION_EDIT, profile))
    }

    @Test
    fun missingProfileOrOtherActionUsesDefault() {
        val payload = Profile()
        payload.name = "Living room"
        val missing = initialProfileForEdit(Intent.ACTION_EDIT, null)
        val other = initialProfileForEdit(null, payload)
        assertEquals("", missing.name)
        assertEquals(null, missing.id)
        assertEquals("", other.name)
    }
}
