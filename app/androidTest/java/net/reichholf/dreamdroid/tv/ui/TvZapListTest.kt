package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvZapListTest {
    @get:Rule
    val composeRule = createComposeRule()

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
        val service = ServiceNowNext(
            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
            serviceName = "Demo Channel",
            now = Event(title = "Now Show"),
            next = Event(title = "Next Show", startTimeReadable = "20:00")
        )
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
        composeRule.onNodeWithTag("hub_service_picon", useUnmergedTree = true).assertExists()
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
        val service = ServiceNowNext(
            serviceReference = "1:0:1:9:9:9:9:0:0:0:",
            serviceName = "ZDF HD",
            now = Event(title = "Heute")
        )
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
}
