package net.reichholf.dreamdroid.ui.nav

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TestProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [PhoneNavigator] across an activity recreation and a process restore. */
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
    fun drawerEpgWithoutAnActiveProfileNavigatesNowhere() {
        profiles.repository.clearCurrent()

        assertFalse(navigator().navigateToDrawerEpg())
    }

    @Test
    fun dialogFlagsAndRemountsLiveInTheSharedState() {
        navigator().requestLeaveConfirm()
        navigator().onActiveProfileChanged()

        val next = navigator().navUiState.value
        assertTrue(next.leaveConfirmRequested)
        assertEquals(1, next.epgRemount)
    }

    @Test
    fun aCheckForTheSameProfileKeepsTheEpgMounted() {
        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        val navigator = navigator()

        navigator.onActiveProfileChanged()
        navigator.onActiveProfileChanged()

        assertEquals(1, state.uiState.value.epgRemount)
    }

    @Test
    fun aRecreatedNavigatorDoesNotRemountForTheSameProfile() {
        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        navigator().onActiveProfileChanged()

        navigator().onActiveProfileChanged()

        assertEquals(1, state.uiState.value.epgRemount)
    }

    @Test
    fun anotherProfileRemountsTheEpg() {
        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        val navigator = navigator()
        navigator.onActiveProfileChanged()

        profiles.repository.setCurrent(profile(id = 2, host = "box"))
        navigator.onActiveProfileChanged()

        assertEquals(2, state.uiState.value.epgRemount)
    }

    @Test
    fun editedConnectionSettingsRemountTheEpg() {
        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        val navigator = navigator()
        navigator.onActiveProfileChanged()

        profiles.repository.setCurrent(profile(id = 1, host = "other-box"))
        navigator.onActiveProfileChanged()

        assertEquals(2, state.uiState.value.epgRemount)
    }

    @Test
    fun aForcedPublishOfTheSameSettingsKeepsTheEpgMounted() = runBlocking<Unit> {
        val stored = profile(host = "box")
        profiles.repository.save(stored)
        val id = checkNotNull(stored.id)
        assertTrue(profiles.repository.setCurrent(id))
        val navigator = navigator()
        navigator.onActiveProfileChanged()

        val before = checkNotNull(profiles.repository.current.value)
        assertTrue(profiles.repository.setCurrent(id, forceEvent = true))
        val after = checkNotNull(profiles.repository.current.value)
        assertNotSame(before, after)
        navigator.onActiveProfileChanged()

        assertEquals(1, state.uiState.value.epgRemount)
    }

    @Test
    fun aProfileAppearingAfterNoneRemountsTheEpg() {
        val navigator = navigator()
        navigator.onActiveProfileChanged()

        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        navigator.onActiveProfileChanged()

        assertEquals(2, state.uiState.value.epgRemount)
    }

    @Test
    fun aRestoredProcessDoesNotRemountForTheSameProfile() {
        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        val handle = SavedStateHandle()
        val before = PhoneNavHostState(handle)
        navigator(before).onActiveProfileChanged()
        val epoch = before.uiState.value.epgRemount
        assertEquals(1, epoch)

        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        val restored = PhoneNavHostState(handle)
        navigator(restored).onActiveProfileChanged()

        assertEquals(epoch, restored.uiState.value.epgRemount)
    }

    @Test
    fun aRestoredProcessRemountsForAnotherProfile() {
        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        val handle = SavedStateHandle()
        val before = PhoneNavHostState(handle)
        navigator(before).onActiveProfileChanged()
        val epoch = before.uiState.value.epgRemount

        profiles.repository.setCurrent(profile(id = 2, host = "box"))
        val restored = PhoneNavHostState(handle)
        navigator(restored).onActiveProfileChanged()

        assertEquals(epoch + 1, restored.uiState.value.epgRemount)
    }

    @Test
    fun aRestoredProcessRemountsForEditedConnectionSettings() {
        profiles.repository.setCurrent(profile(id = 1, host = "box"))
        val handle = SavedStateHandle()
        val before = PhoneNavHostState(handle)
        navigator(before).onActiveProfileChanged()
        val epoch = before.uiState.value.epgRemount

        profiles.repository.setCurrent(profile(id = 1, host = "other-box"))
        val restored = PhoneNavHostState(handle)
        navigator(restored).onActiveProfileChanged()

        assertEquals(epoch + 1, restored.uiState.value.epgRemount)
    }

    @Test
    fun aRestoredProcessWithoutAProfileDoesNotRemount() {
        profiles.repository.clearCurrent()
        val handle = SavedStateHandle()
        val before = PhoneNavHostState(handle)
        navigator(before).onActiveProfileChanged()
        val epoch = before.uiState.value.epgRemount

        val restored = PhoneNavHostState(handle)
        navigator(restored).onActiveProfileChanged()

        assertEquals(epoch, restored.uiState.value.epgRemount)
    }

    private fun profile(id: Int? = null, host: String): Profile = Profile().apply {
        this.id = id
        this.host = host
    }

    private fun navigator(state: PhoneNavHostState = this.state) = PhoneNavigator(
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
