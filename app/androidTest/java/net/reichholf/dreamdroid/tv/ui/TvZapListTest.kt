package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvZapListTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS, false)
            .commit()
    }

    @Test
    fun zapListClickInvokesCallback() {
        var clicked: ServiceNowNext? = null
        val service = demoService("Demo Channel", "1:0:1:1:1:1:1:0:0:0:")
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                TvZapList(
                    services = listOf(service),
                    currentRef = service.serviceReference,
                    onServiceClick = { clicked = it }
                )
            }
        }
        composeRule.onNodeWithTag("overlay_zap_list", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_service_row", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Demo Channel").assertIsDisplayed()
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed().assertHasClickAction()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        if (clicked == null) {
            node.performClick()
        }
        assertEquals(service, clicked)
    }

    @Test
    fun zapListCardIsNotWhiteInNight() {
        val service = demoService("ZDF HD", "1:0:1:9:9:9:9:0:0:0:")
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                TvZapList(
                    services = listOf(service),
                    currentRef = service.serviceReference,
                    onServiceClick = {}
                )
            }
        }
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val x = (bitmap.width * 0.92f).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height * 0.92f).toInt().coerceIn(0, bitmap.height - 1)
        val corner = Color(bitmap.getPixel(x, y))
        assertTrue(
            "overlay zap card should be dark, not white argb=#${Integer.toHexString(
                corner.toArgb()
            )}",
            corner.luminance() < 0.4f
        )
        assertTrue(corner.toArgb() != Color.White.toArgb())
    }

    @Test
    fun zapListFocusAndDpadNotifyUserInteraction() {
        var interactions = 0
        val service = demoService("Das Erste HD", "1:0:1:1:1:1:1:0:0:0:")
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                TvZapList(
                    services = listOf(service),
                    currentRef = service.serviceReference,
                    onServiceClick = {},
                    onUserInteraction = { interactions++ }
                )
            }
        }
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed()
        node.requestFocus()
        composeRule.waitForIdle()
        assertTrue("focus movement should pause/reset overlay autohide", interactions > 0)
        val afterFocus = interactions
        node.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        assertTrue(
            "D-pad should pause/reset overlay autohide (before=$afterFocus after=$interactions)",
            interactions > afterFocus
        )
    }

    @Test
    fun firstItemFocusRequesterFocusesZapCard() {
        val requester = FocusRequester()
        val service = demoService("Demo Channel", "1:0:1:1:1:1:1:0:0:0:")
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                TvZapList(
                    services = listOf(service),
                    currentRef = service.serviceReference,
                    onServiceClick = {},
                    firstItemFocusRequester = requester
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { requester.requestFocus() }
        composeRule.onNodeWithTag("hub_service_card").assertIsFocused()
    }

    @Test
    fun bindTvZapListBridgesViewFocusIntoFirstCard() {
        val service = demoService("Demo Channel", "1:0:1:1:1:1:1:0:0:0:")
        val state = VideoOverlayUiState().apply {
            zapServices = listOf(service)
            zapCurrentRef = service.serviceReference
        }
        var composeView: ComposeView? = null
        composeRule.setContent {
            AndroidView(
                factory = { ctx ->
                    ComposeView(ctx).also { view ->
                        composeView = view
                        view.bindTvZapList(state, onServiceClick = {})
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
        composeRule.waitForIdle()
        val view = composeView!!
        assertTrue(view.isFocusable)
        assertTrue(view.isFocusableInTouchMode)
        composeRule.runOnIdle { view.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_service_card").assertIsFocused()
    }

    private fun demoService(name: String, reference: String): ServiceNowNext = ServiceNowNext(
        serviceReference = reference,
        serviceName = name,
        now = Event(title = "Now Show"),
        next = Event(title = "Next Show", startTimeReadable = "20:00")
    )
}
