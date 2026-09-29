package net.reichholf.dreamdroid.ui.nav

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TestProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [PhoneNavigator] across an activity recreation: a new navigator over the same state. */
class PhoneNavigatorTest {
    private val profiles = TestProfiles()
    private val state = PhoneNavHostState(SavedStateHandle())

    @Test
    fun aResultWithoutListenerReachesTheNextNavigatorsDestination() {
        val before = navigator()
        before.onHostActivityResult(requestCode = 5, resultCode = -1, data = null)

        val after = navigator()
        val delivered = mutableListOf<Pair<Int, Int>>()
        after.composeActivityResultListener =
            PhoneNavHandle.ActivityResultListener { requestCode, resultCode, _: Intent? ->
                delivered += requestCode to resultCode
            }
        after.dispatchPendingComposeActivityResult()
        after.dispatchPendingComposeActivityResult()

        assertEquals(listOf(5 to -1), delivered)
    }

    @Test
    fun aListeningDestinationGetsTheResultAtOnce() {
        val navigator = navigator()
        val delivered = mutableListOf<Int>()
        navigator.composeActivityResultListener =
            PhoneNavHandle.ActivityResultListener { requestCode, _, _ -> delivered += requestCode }

        navigator.onHostActivityResult(requestCode = 3, resultCode = -1, data = null)

        assertEquals(listOf(3), delivered)
        assertTrue(state.takeHeldActivityResult() == null)
    }

    @Test
    fun dialogFlagsAndRemountsLiveInTheSharedState() {
        navigator().requestLeaveConfirm()
        navigator().onActiveProfileChanged()

        val next = navigator().navUiState.value
        assertTrue(next.leaveConfirmRequested)
        assertEquals(1, next.epgRemount)
    }

    private fun navigator() = PhoneNavigator(
        state = state,
        lifecycleOwner = ResumedOwner(),
        highlighter = null,
        profiles = profiles.repository,
        settings = SettingsRepository(MemorySharedPreferences()),
        sessions = profiles.sessions
    )

    private class ResumedOwner : LifecycleOwner {
        override val lifecycle: Lifecycle =
            LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    }
}
