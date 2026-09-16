package net.reichholf.dreamdroid.ui.dialogs

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
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
            InstrumentationRegistry.getInstrumentation().targetContext
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
                    onConfirm = {}
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
                onSurface.luminance() > 0.5f
            )
        }
    }

    @Test
    fun multiChoiceEmptyShowsNoItemsMessage() {
        val emptyMessage = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.no_list_item)
        composeRule.setContent {
            DreamDroidTheme {
                MultiChoiceAlertDialog(
                    title = "Pick tags",
                    items = emptyList(),
                    initialChecked = booleanArrayOf(),
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick tags").assertIsDisplayed()
        composeRule.onNodeWithText(emptyMessage).assertIsDisplayed()
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
                    onChoice = {}
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
                onSurface.luminance() > 0.5f
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
                    message = "Looking for devices"
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
                onSurface.luminance() > 0.5f
            )
        }
    }

    @Test
    fun blankTitleOmitsHeadingAndShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                IndeterminateProgressDialog(
                    title = "",
                    message = "Saving"
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Saving").assertIsDisplayed()
    }

    @Test
    fun indeterminateProgressHonorsBack() {
        composeRule.setContent {
            DreamDroidTheme {
                IndeterminateProgressHost(
                    IndeterminateProgressState(
                        title = "Searching",
                        message = "Looking for devices"
                    )
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Searching").assertIsDisplayed()
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Searching").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(
            composeRule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }

    @Test
    fun confirmContentColorIsOnSurface() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                ConfirmAlertDialog(
                    title = "Delete?",
                    message = "Really delete this item?",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete?").assertIsDisplayed()
        composeRule.onNodeWithText("Really delete this item?").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f
            )
        }
    }

    @Test
    fun simpleChoiceRowsAreRadioButtons() {
        var chosen = -1
        composeRule.setContent {
            DreamDroidTheme {
                SimpleChoiceAlertDialog(
                    title = "Audio tracks",
                    items = listOf("Track 1", "Track 2"),
                    onDismiss = {},
                    onChoice = { chosen = it }
                )
            }
        }
        composeRule.waitForIdle()
        val row = composeRule.onNode(hasText("Track 1") and isSelectable())
            .assertIsDisplayed()
            .getBoundsInRoot()
        val height = row.bottom - row.top
        assertTrue("choice rows are at least 56.dp, height=$height", height >= 56.dp)
        composeRule.onNode(hasText("Track 1") and isSelectable()).performClick()
        composeRule.runOnIdle { assertEquals(0, chosen) }
    }

    @Test
    fun multiChoiceRowsAreCheckboxes() {
        composeRule.setContent {
            DreamDroidTheme {
                MultiChoiceAlertDialog(
                    title = "Pick tags",
                    items = listOf("News", "Sport"),
                    initialChecked = booleanArrayOf(true, false),
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("News") and isToggleable()).assertIsOn()
        composeRule.onNode(hasText("Sport") and isToggleable()).assertIsOff()
        val row = composeRule.onNode(hasText("News") and isToggleable()).getBoundsInRoot()
        val height = row.bottom - row.top
        assertTrue("choice rows are at least 56.dp, height=$height", height >= 56.dp)
    }

    @Test
    fun destructiveConfirmUsesDeleteLabel() {
        composeRule.setContent {
            DreamDroidTheme {
                ConfirmAlertDialog(
                    title = "Delete?",
                    message = "Really delete this item?",
                    onDismiss = {},
                    onConfirm = {},
                    confirmLabel = "Delete",
                    destructive = true
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
