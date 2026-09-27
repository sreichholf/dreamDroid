package net.reichholf.dreamdroid.ui.video

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.ui.nav.ShellMessages
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Player messages show in the player's own snackbar host, not a toast. */
@RunWith(AndroidJUnit4::class)
class VideoSnackbarHostTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun postedMessageShowsInPlayer() {
        val intent = Intent(context, VideoActivity::class.java)
            .putExtra(VideoOverlayController.TITLE, "Snackbar title")
        ActivityScenario.launch<VideoActivity>(intent).use {
            composeRule.waitForIdle()
            ShellMessages.post("Playback failed here")
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("Playback failed here")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithText("Playback failed here").assertExists()
        }
    }
}
