package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.ui.dialogs.MUTATION_PROGRESS_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val NEEDS_RECEIVER_TAG = "tv_timer_editor_needs_receiver"

/**
 * Shared TV add/edit host. Catalogs are prefilled so [TvTimerEditorHost] reloads
 * the form without a receiver round-trip. A profile is still installed because a
 * missing catalog falls through to HTTP, and that path calls
 * [DreamDroid.getCurrentProfile].
 */
class TvTimerEditorHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var previousProfile: Profile? = null
    private var seededLocation = false
    private var seededTag = false

    @Before
    fun seedProfileAndCatalogs() {
        previousProfile = DreamDroid.currentProfileOrNull()
        DreamDroid.setCurrentProfile(
            Profile().apply {
                host = "127.0.0.1"
                port = 80
                streamPort = 8001
            }
        )
        if (DreamDroid.getLocations().isEmpty()) {
            DreamDroid.getLocations().add("/hdd/movie/")
            seededLocation = true
        }
        if (DreamDroid.getTags().isEmpty()) {
            DreamDroid.getTags().add("News")
            seededTag = true
        }
    }

    @After
    fun restoreProfileAndCatalogs() {
        if (seededLocation) {
            DreamDroid.getLocations().remove("/hdd/movie/")
            seededLocation = false
        }
        if (seededTag) {
            DreamDroid.getTags().remove("News")
            seededTag = false
        }
        val previous = previousProfile
        if (previous != null) {
            DreamDroid.setCurrentProfile(previous)
        } else {
            DreamDroid.loadCurrentProfile(targetContext())
        }
    }

    @Test
    fun createModeShowsFormLabelsAndSaveFab() {
        val timer = sampleTimer(name = "Sample")
        composeRule.setContent {
            EditorHost(timer = timer, isCreate = true)
        }
        waitForName(timer.name)
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(serviceLabel())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(saveLabel()).assertIsDisplayed()
    }

    @Test
    fun blockedSaveShowsNeedsReceiverAndDoesNotSaveOrDismiss() {
        var saved = false
        var dismissed = false
        val timer = sampleTimer(name = "Blocked save")
        composeRule.setContent {
            EditorHost(
                timer = timer,
                isCreate = true,
                mutationsBlocked = true,
                onDismiss = { dismissed = true },
                onSaved = { saved = true }
            )
        }
        waitForName(timer.name)
        composeRule.onNodeWithTag(NEEDS_RECEIVER_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(saveLabel()).performClick()
        composeRule.waitForIdle()
        assertFalse(saved)
        assertFalse(dismissed)
        composeRule.onNodeWithTag(NEEDS_RECEIVER_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(needsReceiverLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(MUTATION_PROGRESS_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("${NEEDS_RECEIVER_TAG}_ok").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NEEDS_RECEIVER_TAG).assertDoesNotExist()
        assertFalse(saved)
        assertFalse(dismissed)
    }

    @Test
    fun unblockedHostDoesNotShowNeedsReceiverOnLaunch() {
        var saved = false
        composeRule.setContent {
            EditorHost(
                timer = sampleTimer(name = "Sample"),
                isCreate = true,
                onSaved = { saved = true }
            )
        }
        waitForName("Sample")
        composeRule.onNodeWithContentDescription(saveLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(NEEDS_RECEIVER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(MUTATION_PROGRESS_TAG).assertDoesNotExist()
        assertFalse(saved)
    }

    @Test
    fun editModeShowsExistingTimerName() {
        val timer = sampleTimer(name = "Tagesschau")
        composeRule.setContent {
            EditorHost(timer = timer, isCreate = false)
        }
        waitForName(timer.name)
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
    }

    @Composable
    private fun EditorHost(
        timer: Timer,
        isCreate: Boolean,
        mutationsBlocked: Boolean = false,
        onDismiss: () -> Unit = {},
        onSaved: () -> Unit = {}
    ) {
        DreamDroidTvTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                TvTimerEditorHost(
                    timer = timer,
                    isCreate = isCreate,
                    onDismiss = onDismiss,
                    onSaved = onSaved,
                    mutationsBlocked = mutationsBlocked
                )
            }
        }
    }

    private fun waitForName(name: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(name).assertIsDisplayed()
    }

    private fun saveLabel(): String = targetContext().getString(R.string.save)

    private fun serviceLabel(): String = targetContext().getString(R.string.service)

    private fun needsReceiverLabel(): String =
        targetContext().getString(R.string.session_needs_receiver)

    private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleTimer(name: String): Timer = TimerHelper.getInitialTimer().copy(
        name = name,
        description = "Desc",
        serviceName = "Das Erste HD",
        reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
        begin = "1893456000",
        end = "1893459600",
        disabled = "0",
        justPlay = "0",
        afterEvent = "3",
        location = "/hdd/movie/",
        repeated = "0",
        tags = ""
    )
}
