package net.reichholf.dreamdroid.ui.dialogs

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2.1g-ii-e: Multi/simple choice + indeterminate progress are Compose M3 AlertDialogs
 * (no DialogFragment). Hosted under DreamDroidTheme so night LocalContentColor stays onSurface.
 */
class ChoiceDialogsHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun multiChoiceContentColorIsOnSurface() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                MultiChoiceAlertDialog(
                    title = "Pick tags",
                    items = listOf("News", "Sport"),
                    initialChecked = booleanArrayOf(true, false),
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick tags").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
        composeRule.onNodeWithText("Sport").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }

    @Test
    fun simpleChoiceContentColorIsOnSurface() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                SimpleChoiceAlertDialog(
                    title = "Audio tracks",
                    items = listOf("Track 1", "Track 2"),
                    onDismiss = {},
                    onChoice = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Audio tracks").assertIsDisplayed()
        composeRule.onNodeWithText("Track 1").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }

    @Test
    fun indeterminateProgressContentColorIsOnSurface() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                IndeterminateProgressDialog(
                    title = "Searching",
                    message = "Looking for devices",
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Searching").assertIsDisplayed()
        composeRule.onNodeWithText("Looking for devices").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }
}
