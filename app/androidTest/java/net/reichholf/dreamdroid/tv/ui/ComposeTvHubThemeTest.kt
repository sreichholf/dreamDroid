package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposeTvHubThemeTest {
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
    fun settingsCardsUsePhoneSurfacesNotWhite() {
        var cardColor = Color.Unspecified
        composeRule.setContent {
            DreamDroidTvTheme {
                cardColor = PhoneMaterialTheme.colorScheme.surfaceContainerLow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    HubSettingsRow(
                        settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                        onSettingsClick = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag("hub_settings_icon_reload").assertIsDisplayed()
        val bitmap = composeRule
            .onNodeWithTag("hub_settings_reload")
            .captureToImage()
            .asAndroidBitmap()
        val expected = cardColor.toArgb()
        val white = Color.White.toArgb()
        val x = (bitmap.width * 0.92f).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height * 0.92f).toInt().coerceIn(0, bitmap.height - 1)
        val px = bitmap.getPixel(x, y)
        assertTrue(
            "settings card should use phone surfaceContainerLow, not white " +
                "(px=#${Integer.toHexString(px)} expected=#${Integer.toHexString(expected)} " +
                "${bitmap.width}x${bitmap.height})",
            rgbDistance(px, expected) < 50
        )
        assertTrue(
            "settings card must not be default TV Material 3 white",
            rgbDistance(px, white) > 80
        )
    }

    private fun rgbDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xff
        val ag = (a shr 8) and 0xff
        val ab = a and 0xff
        val br = (b shr 16) and 0xff
        val bg = (b shr 8) and 0xff
        val bb = b and 0xff
        return abs(ar - br) + abs(ag - bg) + abs(ab - bb)
    }
}
