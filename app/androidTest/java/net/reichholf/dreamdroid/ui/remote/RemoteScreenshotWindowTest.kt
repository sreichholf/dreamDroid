package net.reichholf.dreamdroid.ui.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.testutil.COMPACT_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.EXPANDED_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.WithWindowSize
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The virtual remote's screenshot by window size class: which windows show it, and that it gets
 * room next to the pad, which fills whatever it is given.
 */
class RemoteScreenshotWindowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aTabletWindowShowsTheScreenshot() {
        assertTrue(showsScreenshot(EXPANDED_WINDOW_WIDTH, height = 900.dp))
    }

    @Test
    fun anUprightTabletShowsTheScreenshot() {
        assertTrue(showsScreenshot(600.dp, height = 900.dp))
    }

    @Test
    fun aMediumWindowWithoutRoomForBothKeepsThePadAlone() {
        assertFalse(showsScreenshot(700.dp, height = 800.dp))
    }

    @Test
    fun anUprightPhoneKeepsThePadAlone() {
        assertFalse(showsScreenshot(COMPACT_WINDOW_WIDTH, height = 860.dp))
    }

    @Test
    fun aPhoneInLandscapeKeepsThePadAlone() {
        assertFalse(showsScreenshot(860.dp, height = 400.dp))
    }

    @Test
    fun anExpandedWindowPutsTheScreenshotBesideThePad() {
        val (screenshot, pad) = layOut(EXPANDED_WINDOW_WIDTH)

        assertTrue("screenshot has room", screenshot.width > 0f && screenshot.height > 0f)
        assertTrue("pad beside it", pad.left >= screenshot.right)
    }

    @Test
    fun aMediumWindowPutsTheScreenshotAboveThePad() {
        val (screenshot, pad) = layOut(700.dp)

        assertTrue("screenshot has room", screenshot.width > 0f && screenshot.height > 0f)
        assertTrue("pad below it", pad.top >= screenshot.bottom)
    }

    private fun showsScreenshot(width: Dp, height: Dp): Boolean {
        var shows: Boolean? = null
        composeRule.setContent {
            WithWindowSize(width, height) { shows = showsRemoteScreenshot() }
        }
        composeRule.waitForIdle()
        return checkNotNull(shows)
    }

    private fun layOut(width: Dp): Pair<Rect, Rect> {
        composeRule.setContent {
            DreamDroidTheme {
                WithWindowSize(width) {
                    RemoteWithScreenshot(
                        screenshot = { Box(it.testTag(SCREENSHOT_TAG)) },
                        pad = {
                            VirtualRemoteScreen(
                                layout = VirtualRemoteLayout.Full,
                                playButtonAsPlayPause = false,
                                onKey = { _, _ -> },
                                modifier = it.testTag(PAD_TAG)
                            )
                        }
                    )
                }
            }
        }
        return bounds(SCREENSHOT_TAG) to bounds(PAD_TAG)
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val SCREENSHOT_TAG = "remote_screenshot"
        const val PAD_TAG = "remote_pad"
    }
}
