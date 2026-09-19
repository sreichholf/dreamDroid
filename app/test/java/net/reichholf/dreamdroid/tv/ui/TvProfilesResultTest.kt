package net.reichholf.dreamdroid.tv.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvProfilesResultTest {
    @Test
    fun listBackFinishesWithoutSettingOk() {
        val policy = tvProfilesResultPolicy(TvProfilesEvent.ListBack)
        assertFalse(policy.setResultOk)
        assertTrue(policy.finish)
    }

    @Test
    fun activateSuccessSetsOkAndFinishes() {
        val policy = tvProfilesResultPolicy(TvProfilesEvent.Activate(success = true))
        assertTrue(policy.setResultOk)
        assertTrue(policy.finish)
    }

    @Test
    fun activateFailureLeavesResultAndDoesNotFinish() {
        val policy = tvProfilesResultPolicy(TvProfilesEvent.Activate(success = false))
        assertFalse(policy.setResultOk)
        assertFalse(policy.finish)
    }

    @Test
    fun saveCurrentSetsOkAndStays() {
        val policy = tvProfilesResultPolicy(
            TvProfilesEvent.Save(saved = true, currentProfile = true)
        )
        assertTrue(policy.setResultOk)
        assertFalse(policy.finish)
    }

    @Test
    fun saveOtherOrAddLeavesResultAndDoesNotFinish() {
        val other = tvProfilesResultPolicy(
            TvProfilesEvent.Save(saved = true, currentProfile = false)
        )
        assertFalse(other.setResultOk)
        assertFalse(other.finish)
        val add = tvProfilesResultPolicy(
            TvProfilesEvent.Save(saved = true, currentProfile = false)
        )
        assertEquals(other, add)
    }

    @Test
    fun saveFailedLeavesResultAndDoesNotFinish() {
        val policy = tvProfilesResultPolicy(
            TvProfilesEvent.Save(saved = false, currentProfile = false)
        )
        assertFalse(policy.setResultOk)
        assertFalse(policy.finish)
    }

    @Test
    fun deleteCurrentSetsOkAndFinishes() {
        val policy = tvProfilesResultPolicy(TvProfilesEvent.Delete(currentProfile = true))
        assertTrue(policy.setResultOk)
        assertTrue(policy.finish)
    }

    @Test
    fun deleteOtherLeavesResultAndDoesNotFinish() {
        val policy = tvProfilesResultPolicy(TvProfilesEvent.Delete(currentProfile = false))
        assertFalse(policy.setResultOk)
        assertFalse(policy.finish)
    }
}
