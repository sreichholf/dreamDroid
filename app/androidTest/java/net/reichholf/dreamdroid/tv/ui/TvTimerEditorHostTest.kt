package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Shared TV add/edit host. Catalogs are prefilled so [TvTimerEditorHost] reloads
 * the form without a receiver round-trip. A profile is still installed because a
 * missing catalog falls through to HTTP, and that path calls
 * [DreamDroid.getCurrentProfile].
 */
@OptIn(ExperimentalTestApi::class)
class TvTimerEditorHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
    fun createModeShowsTimerNameAndSaveFab() {
        val timer = sampleTimer(name = "Sample")
        composeRule.setContent {
            EditorHost(timer = timer, isCreate = true)
        }
        waitForName(timer.name)
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription(saveLabel()).performClick()
        composeRule.waitForIdle()
        assertFalse(saved)
        assertFalse(dismissed)
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()
        dismissNeedsReceiver()
        composeRule.onNodeWithTag("hub_stream_unavailable").assertDoesNotExist()
        assertFalse(saved)
        assertFalse(dismissed)
    }

    /**
     * OK is a TV Surface. It handles D-pad center. Android [performClick] is a
     * touch click, which that surface ignores.
     */
    private fun dismissNeedsReceiver() {
        val ok = composeRule.onNodeWithTag("hub_stream_unavailable_ok")
        ok.assertIsDisplayed()
        ok.requestFocus()
        ok.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (
            composeRule.onAllNodesWithTag("hub_stream_unavailable")
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            ok.performClick()
            composeRule.waitForIdle()
        }
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

    @Test
    fun editedTitleSurvivesActivityRecreation() {
        val timer = sampleTimer(name = "Before recreate")
        setActivityContent { EditorHost(timer = timer, isCreate = false) }
        waitForName(timer.name)
        composeRule.onNodeWithContentDescription("Title")
            .performTextReplacement("After recreate")

        composeRule.activityRule.scenario.recreate()
        setActivityContent { EditorHost(timer = timer, isCreate = false) }

        waitForName("After recreate")
        composeRule.onNodeWithText(timer.name).assertDoesNotExist()
    }

    @Test
    fun reopenAfterLeavingCompositionStartsFromLaunchTimer() {
        val timer = sampleTimer(name = "Launch name")
        var shown by mutableStateOf(true)
        composeRule.setContent {
            if (shown) {
                EditorHost(timer = timer, isCreate = false)
            }
        }
        waitForName(timer.name)
        composeRule.onNodeWithContentDescription("Title")
            .performTextReplacement("Discarded edit")
        composeRule.onNodeWithText("Discarded edit").assertIsDisplayed()

        shown = false
        composeRule.waitForIdle()
        shown = true

        waitForName(timer.name)
        composeRule.onNodeWithText("Discarded edit").assertDoesNotExist()
    }

    /** The rule's own setContent can only run once, on the activity recreate() replaces. */
    private fun setActivityContent(content: @Composable () -> Unit) {
        composeRule.runOnUiThread { composeRule.activity.setContent(content = content) }
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

    private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleTimer(name: String): Timer = TimerHelper.getInitialTimer().copy(
        name = name,
        description = "Desc",
        serviceName = "Das Erste HD",
        reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
        disabled = "0",
        justPlay = "0",
        afterEvent = "3",
        location = "/hdd/movie/",
        repeated = "0",
        tags = ""
    )
}
