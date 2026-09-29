package net.reichholf.dreamdroid.ui.nav

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNavHostStateTest {
    @Test
    fun startRouteAndRequestCodesSurviveProcessDeath() {
        val handle = SavedStateHandle()
        val first = PhoneNavHostState(handle)
        assertFalse(first.hasSavedStartRoute())
        first.setStartRoute(PhoneNavRoutes.REMOTE)
        first.pushResultRequestCode(7)
        first.pushResultRequestCode(9)

        val restored = PhoneNavHostState(handle)

        assertTrue(restored.hasSavedStartRoute())
        assertEquals(PhoneNavRoutes.REMOTE, restored.startRoute)
        assertEquals(9, restored.popResultRequestCode())
        assertEquals(7, restored.popResultRequestCode())
        assertNull(restored.popResultRequestCode())
    }

    @Test
    fun clearingTheRequestCodesIsSaved() {
        val handle = SavedStateHandle()
        val first = PhoneNavHostState(handle)
        first.pushResultRequestCode(7)
        first.clearResultRequestCodes()

        assertNull(PhoneNavHostState(handle).popResultRequestCode())
    }

    @Test
    fun theNavSchemaIsMarkedOnce() {
        val handle = SavedStateHandle()
        assertFalse(PhoneNavHostState(handle).hasNavSchema())

        PhoneNavHostState(handle).markNavSchema()

        assertTrue(PhoneNavHostState(handle).hasNavSchema())
    }

    @Test
    fun aHeldResultIsTakenOnce() {
        val state = PhoneNavHostState(SavedStateHandle())
        assertNull(state.takeHeldActivityResult())

        state.holdActivityResult(PendingComposeActivityResult(1, -1), null)
        state.holdActivityResult(PendingComposeActivityResult(5, -1), null)

        assertEquals(PendingComposeActivityResult(5, -1) to null, state.takeHeldActivityResult())
        assertNull(state.takeHeldActivityResult())
    }

    @Test
    fun uiStateCountsRemountsAndFollowsTheDialogs() {
        val state = PhoneNavHostState(SavedStateHandle())

        state.remountEpg()
        state.remountEpg()
        state.remountEpgSearch()
        state.requestLeaveConfirm()
        state.requestNeedsReceiver()
        assertEquals(
            PhoneNavUiState(
                epgRemount = 2,
                epgSearchRemount = 1,
                leaveConfirmRequested = true,
                needsReceiverRequested = true
            ),
            state.uiState.value
        )

        state.clearLeaveConfirm()
        state.clearNeedsReceiver()
        assertFalse(state.uiState.value.leaveConfirmRequested)
        assertFalse(state.uiState.value.needsReceiverRequested)
    }
}
