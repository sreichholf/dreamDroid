package net.reichholf.dreamdroid.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * TV hub [DreamDroidTvTheme] must reuse the phone/tablet palette. Default
 * androidx.tv Material 3 is light (white cards) even on a Leanback window.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
class DreamDroidTvThemeTest {
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
    fun tvSchemeMatchesPhoneNightTokensNotDefaultWhite() {
        var phonePrimary = Color.Unspecified
        var phoneOnSurface = Color.Unspecified
        var phoneBackground = Color.Unspecified
        var phoneSurface = Color.Unspecified
        var tvPrimary = Color.Unspecified
        var tvOnSurface = Color.Unspecified
        var tvBackground = Color.Unspecified
        var tvSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTvTheme {
                val phone = PhoneMaterialTheme.colorScheme
                phonePrimary = phone.primary
                phoneOnSurface = phone.onSurface
                phoneBackground = phone.background
                phoneSurface = phone.surface
                val tv = TvMaterialTheme.colorScheme
                tvPrimary = tv.primary
                tvOnSurface = tv.onSurface
                tvBackground = tv.background
                tvSurface = tv.surface
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(phonePrimary, tvPrimary)
            assertEquals(phoneOnSurface, tvOnSurface)
            assertEquals(phoneBackground, tvBackground)
            assertEquals(phoneSurface, tvSurface)
            assertTrue(
                "night TV onSurface should be light, luminance=${tvOnSurface.luminance()}",
                tvOnSurface.luminance() > 0.5f
            )
            assertTrue(
                "night TV background should be dark, luminance=${tvBackground.luminance()}",
                tvBackground.luminance() < 0.4f
            )
            assertNotEquals(Color.White, tvSurface)
            assertNotEquals(Color(0xFF6750A4), tvPrimary)
        }
    }

    /** The resting card fill is the phone's `surfaceContainerLow`, not TV Material 3's surface. */
    @Test
    fun cardColorsUseTheElevatedSurface() {
        var surfaceLow = Color.Unspecified
        composeRule.setContent {
            DreamDroidTvTheme {
                surfaceLow = PhoneMaterialTheme.colorScheme.surfaceContainerLow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Surface(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("tv_card_surface"),
                        colors = dreamDroidTvCardColors()
                    ) {}
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule
            .onNodeWithTag("tv_card_surface")
            .captureToImage()
            .asAndroidBitmap()
        val x = (bitmap.width * 0.5f).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height * 0.5f).toInt().coerceIn(0, bitmap.height - 1)
        val px = Color(bitmap.getPixel(x, y))
        val expected = surfaceLow.toArgb()
        assertTrue(
            "night card container should be dark, luminance=${px.luminance()}",
            px.luminance() < 0.4f
        )
        assertNotEquals(Color.White, px)
        assertTrue(
            "card fill should match phone surfaceContainerLow " +
                "px=#${Integer.toHexString(px.toArgb())} " +
                "expected=#${Integer.toHexString(expected)}",
            rgbDistance(px.toArgb(), expected) < 50
        )
    }

    /**
     * Focus is primary, not the selection tint: the tint is 1.2:1 against the resting card fill
     * (light) and 1.65:1 (night), and a focused card must not look like a selected one at rest.
     * An item resting in the tint focuses in the inverse fill so focus still shows on it. Both
     * fills clear WCAG 1.4.11's 3:1 against the fill they replace.
     */
    @Test
    fun focusedCardsStandOutFromTheirRestingFillInBothThemes() {
        var dark by mutableStateOf(true)
        var restingInSelection by mutableStateOf(false)
        var selection = Color.Unspecified
        var surfaceLow = Color.Unspecified
        var primary = Color.Unspecified
        var inverse = Color.Unspecified
        val focus = FocusRequester()
        composeRule.setContent {
            DreamDroidTvTheme(forceDark = dark) {
                val phone = PhoneMaterialTheme.colorScheme
                selection = phone.secondaryContainer
                surfaceLow = phone.surfaceContainerLow
                primary = phone.primary
                inverse = phone.inverseSurface
                Surface(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .focusRequester(focus)
                        .testTag("tv_card_surface"),
                    colors = dreamDroidTvCardColors(
                        restContainer = phone.secondaryContainer.takeIf { restingInSelection }
                    ),
                    scale = ClickableSurfaceScale.None
                ) {}
            }
        }
        composeRule.runOnIdle { focus.requestFocus() }
        for (theme in listOf(true, false)) {
            for (resting in listOf(false, true)) {
                composeRule.runOnIdle {
                    dark = theme
                    restingInSelection = resting
                }
                composeRule.waitForIdle()
                val bitmap = composeRule.onNodeWithTag("tv_card_surface").captureToImage()
                    .asAndroidBitmap()
                val px = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                val rest = if (resting) selection else surfaceLow
                val want = if (resting) inverse else primary
                assertTrue(
                    "dark=$theme selectedResting=$resting focused #${Integer.toHexString(px)} " +
                        "expected #${Integer.toHexString(want.toArgb())}",
                    rgbDistance(px, want.toArgb()) < 30
                )
                assertTrue(
                    "dark=$theme selectedResting=$resting focus #${Integer.toHexString(px)} " +
                        "on rest #${Integer.toHexString(rest.toArgb())} " +
                        "contrast=${contrast(px, rest.toArgb())}",
                    contrast(px, rest.toArgb()) >= 3f
                )
            }
        }
    }

    /** WCAG relative-luminance contrast of two ARGB colours. */
    private fun contrast(a: Int, b: Int): Float {
        val la = Color(a).luminance()
        val lb = Color(b).luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
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

    @Test
    fun televisionXmlThemeIsMaterial3NotLeanback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedPrimary = context.getColor(R.color.md_theme_dark_primary)
        val expectedBackground = context.getColor(R.color.md_theme_dark_background)
        val tvNight = Configuration(context.resources.configuration).apply {
            uiMode = (
                uiMode and Configuration.UI_MODE_TYPE_MASK.inv() and
                    Configuration.UI_MODE_NIGHT_MASK.inv()
                ) or
                Configuration.UI_MODE_TYPE_TELEVISION or
                Configuration.UI_MODE_NIGHT_YES
        }
        val tvContext = context.createConfigurationContext(tvNight)
        val theme = tvContext.resources.newTheme()
        theme.applyStyle(R.style.Theme_DreamDroid, true)
        val attrs = intArrayOf(
            androidx.appcompat.R.attr.colorPrimary,
            android.R.attr.colorBackground
        )
        val ta = theme.obtainStyledAttributes(attrs)
        val primary = ta.getColor(0, 0)
        val background = ta.getColor(1, 0)
        ta.recycle()
        assertEquals(expectedPrimary, primary)
        assertEquals(expectedBackground, background)
    }
}
