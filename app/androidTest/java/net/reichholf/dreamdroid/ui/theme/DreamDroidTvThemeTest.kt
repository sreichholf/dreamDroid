package net.reichholf.dreamdroid.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
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
