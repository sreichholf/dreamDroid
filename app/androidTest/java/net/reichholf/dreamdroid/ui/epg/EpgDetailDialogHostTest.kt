package net.reichholf.dreamdroid.ui.epg

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EpgDetailDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun contentColorIsOnSurfaceInsideNightBottomSheet() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        var setTimerClicks = 0
        val content = EpgDetailContent(
            title = "Tagesschau",
            serviceName = "Das Erste HD",
            description = "News",
            descriptionExtended = "Die Nachrichten um 20 Uhr.",
            dateLine = "20:00 (15 min)",
            isNext = false,
        )
        val activity = composeRule.activity
        composeRule.runOnUiThread {
            val themed = ContextThemeWrapper(activity, R.style.Theme_DreamDroid_Night)
            val composeView = ComposeView(themed).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    DreamDroidTheme {
                        localContent = LocalContentColor.current
                        onSurface = MaterialTheme.colorScheme.onSurface
                        EpgDetailScreen(
                            content = content,
                            onSetTimer = { setTimerClicks++ },
                            onEditTimer = {},
                            onImdb = {},
                            onSimilar = {},
                        )
                    }
                }
            }
            BottomSheetDialog(themed).apply {
                setContentView(composeView)
                show()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText("Set Timer").assertIsDisplayed().performClick()
        assertEquals(1, setTimerClicks)
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }
}
