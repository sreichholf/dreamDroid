package net.reichholf.dreamdroid.ui.video

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.testutil.CurrentProfileRule
import net.reichholf.dreamdroid.ui.text.UiText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The ViewModel's user message shows in the player's own snackbar host, not a toast. */
@RunWith(AndroidJUnit4::class)
class VideoSnackbarHostTest {
    @get:Rule(order = 0)
    val currentProfile = CurrentProfileRule()

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun userMessageShowsInPlayer() {
        val intent = Intent(context, VideoActivity::class.java)
            .putExtra(VideoOverlayController.TITLE, "Snackbar title")
        ActivityScenario.launch<VideoActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[VideoPlaybackViewModel::class.java]
                    .showMessage(UiText.Raw("Playback failed here"))
            }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("Playback failed here")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithText("Playback failed here").assertExists()
        }
    }
}
