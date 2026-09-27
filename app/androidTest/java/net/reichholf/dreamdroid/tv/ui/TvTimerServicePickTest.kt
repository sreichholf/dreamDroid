package net.reichholf.dreamdroid.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.pick.TimerServicePickViewModel
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The receiver is unreachable (127.0.0.1), so loads fail fast. Rows are seeded onto the
 * session after that settles.
 */
@OptIn(ExperimentalTestApi::class)
class TvTimerServicePickTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var previousProfile: Profile? = null

    @Before
    fun installProfile() {
        previousProfile = ProfileRepository.get().current.value
        ProfileRepository.get().setCurrent(
            Profile().apply {
                id = 4711
                host = "127.0.0.1"
                port = 80
                streamPort = 8001
            }
        )
    }

    @After
    fun restoreProfile() {
        val previous = previousProfile
        if (previous != null) {
            ProfileRepository.get().setCurrent(previous)
        } else {
            ProfileRepository.get().loadCurrent(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
        }
    }

    @Test
    fun screenShowsRowsAndDeliversDpadClick() {
        var clicked: Service? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                TvTimerServicePickScreen(
                    rows = listOf(CHANNEL),
                    emptyMessage = null,
                    onRowClick = { clicked = it }
                )
            }
        }
        pressCenterOn(CHANNEL.name)
        assertEquals(CHANNEL, clicked)
    }

    @Test
    fun screenShowsEmptyMessageWithoutRows() {
        composeRule.setContent {
            DreamDroidTvTheme {
                TvTimerServicePickScreen(
                    rows = emptyList(),
                    emptyMessage = "Nothing here",
                    onRowClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    @Test
    fun reopenedPickerKeepsBouquetAndLoadedChannels() {
        var open by mutableStateOf(true)
        var picked: Service? = null
        lateinit var owner: ViewModelStoreOwner
        composeRule.setContent {
            owner = checkNotNull(LocalViewModelStoreOwner.current)
            DreamDroidTvTheme {
                if (open) {
                    TvTimerServicePick(
                        onPicked = { picked = it },
                        onDismiss = { open = false }
                    )
                }
            }
        }
        val viewModel = pickViewModel(owner)
        awaitLoadSettled(viewModel)

        composeRule.runOnIdle {
            viewModel.session.listState.replaceAll(listOf(MARKER, BOUQUET))
        }
        composeRule.onNodeWithText(BOUQUET.name).assertIsDisplayed()
        composeRule.onNodeWithText(MARKER.name).assertDoesNotExist()

        pressCenterOn(BOUQUET.name)
        assertEquals(BOUQUET.reference, viewModel.session.bouquetRef)
        awaitLoadSettled(viewModel)
        composeRule.runOnIdle {
            viewModel.session.listState.replaceAll(listOf(CHANNEL))
        }
        composeRule.onNodeWithText(CHANNEL.name).assertIsDisplayed()

        composeRule.runOnIdle { open = false }
        composeRule.onNodeWithTag("tv_timer_service_pick").assertDoesNotExist()
        composeRule.runOnIdle { open = true }

        composeRule.onNodeWithText(CHANNEL.name).assertIsDisplayed()
        assertSame(viewModel, pickViewModel(owner))
        assertEquals(BOUQUET.reference, viewModel.session.bouquetRef)
        assertFalse(viewModel.session.refresh.isRefreshing)

        pressCenterOn(CHANNEL.name)
        assertEquals(CHANNEL, picked)
    }

    private fun pickViewModel(owner: ViewModelStoreOwner): TimerServicePickViewModel =
        composeRule.runOnIdle {
            ViewModelProvider(owner)[tvTimerServicePickKey(), TimerServicePickViewModel::class.java]
        }

    private fun awaitLoadSettled(viewModel: TimerServicePickViewModel) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !viewModel.session.refresh.isRefreshing
        }
    }

    /** TV Surfaces handle D-pad center; a touch [performClick] is ignored. */
    private fun pressCenterOn(text: String) {
        val node = composeRule.onNodeWithText(text)
        node.assertIsDisplayed()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
    }

    private companion object {
        val MARKER = Service("1:64:0:0:0:0:0:0:0:0::Section", "Section")
        val BOUQUET = Service(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            "Favourites"
        )
        val CHANNEL = Service("1:0:1:6DCA:44D:1:C00000:0:0:0:", "Das Erste HD")
    }
}
