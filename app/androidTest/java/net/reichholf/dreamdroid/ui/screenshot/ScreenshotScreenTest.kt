package net.reichholf.dreamdroid.ui.screenshot

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScreenshotScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun actionsEnabledShowsReloadShareSave() {
        val state = ScreenshotUiState().apply { actionsEnabled = true }
        setScreenshotContent(state)
        composeRule.onNodeWithContentDescription("Screenshot").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reload").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save").assertIsDisplayed()
    }

    @Test
    fun actionsDisabledHidesToolbarControls() {
        val state = ScreenshotUiState().apply { actionsEnabled = false }
        setScreenshotContent(state)
        composeRule.onNodeWithContentDescription("Screenshot").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reload").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Save").assertDoesNotExist()
    }

    @Test
    fun loadingShowsIndeterminateSpinner() {
        val state = ScreenshotUiState().apply { loading = true }
        setScreenshotContent(state)
        composeRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun bitmapKeepsScreenshotSemantics() {
        val state = ScreenshotUiState().apply { bitmap = sampleBitmap() }
        setScreenshotContent(state)
        composeRule.onNodeWithContentDescription("Screenshot").assertIsDisplayed()
        composeRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertDoesNotExist()
    }

    @Test
    fun pinchZoomIncreasesScale() {
        val state = ScreenshotUiState().apply { bitmap = sampleBitmap() }
        setScreenshotContent(state)
        composeRule.onNodeWithContentDescription("Screenshot").performTouchInput {
            pinch(
                start0 = center - Offset(40f, 40f),
                start1 = center + Offset(40f, 40f),
                end0 = center - Offset(180f, 180f),
                end1 = center + Offset(180f, 180f)
            )
        }
        composeRule.onNodeWithContentDescription("Screenshot").assert(hasZoomScale { it > 1.2f })
    }

    @Test
    fun applyScreenshotZoomScalesAroundCentroidAndClampsPan() {
        val container = Size(200f, 200f)
        val zoomed = applyScreenshotZoom(
            scale = 1f,
            offset = Offset.Zero,
            centroid = Offset(100f, 100f),
            pan = Offset.Zero,
            zoom = 2f,
            containerSize = container,
            imageWidth = 200,
            imageHeight = 200
        )
        assertEquals(2f, zoomed.scale, 0.01f)
        assertEquals(0f, zoomed.offset.x, 0.01f)
        assertEquals(0f, zoomed.offset.y, 0.01f)

        val panned = applyScreenshotZoom(
            scale = zoomed.scale,
            offset = zoomed.offset,
            centroid = Offset(100f, 100f),
            pan = Offset(1000f, 0f),
            zoom = 1f,
            containerSize = container,
            imageWidth = 200,
            imageHeight = 200
        )
        assertEquals(2f, panned.scale, 0.01f)
        assertEquals(100f, panned.offset.x, 0.01f)
        assertEquals(0f, panned.offset.y, 0.01f)

        val fromLeft = applyScreenshotZoom(
            scale = 1f,
            offset = Offset.Zero,
            centroid = Offset(0f, 100f),
            pan = Offset.Zero,
            zoom = 2f,
            containerSize = container,
            imageWidth = 200,
            imageHeight = 200
        )
        assertEquals(2f, fromLeft.scale, 0.01f)
        assertEquals(100f, fromLeft.offset.x, 0.01f)
        assertEquals(0f, fromLeft.offset.y, 0.01f)

        val overZoom = applyScreenshotZoom(
            scale = 1f,
            offset = Offset.Zero,
            centroid = Offset(100f, 100f),
            pan = Offset.Zero,
            zoom = 100f,
            containerSize = container,
            imageWidth = 200,
            imageHeight = 200
        )
        assertEquals(SCREENSHOT_MAX_SCALE, overZoom.scale, 0.01f)
    }

    private fun setScreenshotContent(state: ScreenshotUiState) {
        composeRule.setContent {
            DreamDroidTheme {
                ScreenshotScreen(
                    state = state,
                    onReload = {},
                    onShare = {},
                    onSave = {}
                )
            }
        }
    }

    private fun sampleBitmap(): Bitmap =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }

    private fun hasZoomScale(predicate: (Float) -> Boolean) =
        SemanticsMatcher("Screenshot zoom scale") { node ->
            val scale = node.config.getOrNull(ScreenshotZoomScale)
            scale != null && predicate(scale)
        }
}
