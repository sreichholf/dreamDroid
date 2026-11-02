package net.reichholf.dreamdroid.ui.dialogs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
 * [TextInputDialog] edits a [TextFieldState] the caller owns, so it holds no state of its own:
 * each test renders the dialog it needs and asserts the label, the error line or the callback.
 */
class TextInputDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Labels from the app's resources, so a locale change does not break the test. */
    private val okText: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ok)

    private val cancelText: String
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.cancel)

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun titleAndDefaultLabelsRender() {
        val state = TextFieldState(INITIAL)
        composeRule.setContent {
            DreamDroidTheme {
                TextInputDialog(
                    title = TITLE,
                    state = state,
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(okText).assertIsDisplayed()
        composeRule.onNodeWithText(cancelText).assertIsDisplayed()
    }

    @Test
    fun customConfirmLabelReplacesOk() {
        show(confirmLabel = "Apply")

        composeRule.onNodeWithText("Apply").assertIsDisplayed()
        composeRule.onNodeWithText(okText).assertDoesNotExist()
    }

    @Test
    fun cancelCallsOnDismissOnly() {
        var dismissCount = 0
        var confirmCount = 0
        show(onDismiss = { dismissCount++ }, onConfirm = { confirmCount++ })

        composeRule.onNodeWithText(cancelText).performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(0, confirmCount)
        }
    }

    @Test
    fun confirmCallsOnConfirmOnly() {
        var dismissCount = 0
        var confirmCount = 0
        show(onDismiss = { dismissCount++ }, onConfirm = { confirmCount++ })

        composeRule.onNodeWithText(okText).performClick()

        composeRule.runOnIdle {
            assertEquals(1, confirmCount)
            assertEquals(0, dismissCount)
        }
    }

    @Test
    fun errorRendersInTheFieldsSupportingSlotAndHidesTheSupportingText() {
        show(error = ERROR, supportingText = SUPPORTING)

        composeRule.onNodeWithText(ERROR).assertIsDisplayed()
        composeRule.onNodeWithText(SUPPORTING).assertDoesNotExist()
        composeRule.onNode(hasErrorSemantics()).assertExists()

        // The line belongs to the text block of the dialog: below the title, above the buttons.
        val title = composeRule.onNodeWithText(TITLE).getBoundsInRoot()
        val errorLine = composeRule.onNodeWithText(ERROR).getBoundsInRoot()
        val confirm = composeRule.onNodeWithText(okText).getBoundsInRoot()
        assertTrue(
            "error line between title and buttons, title=$title line=$errorLine " +
                "confirm=$confirm",
            errorLine.top >= title.bottom && errorLine.bottom <= confirm.top
        )
    }

    @Test
    fun supportingTextRendersWithoutAnError() {
        show(supportingText = SUPPORTING)

        composeRule.onNodeWithText(SUPPORTING).assertIsDisplayed()
        composeRule.onNode(hasErrorSemantics()).assertDoesNotExist()
    }

    @Test
    fun prefilledTextRendersAndTypingReachesTheCallerState() {
        val state = TextFieldState(INITIAL)
        show(state = state)

        composeRule.onNodeWithText(INITIAL).assertIsDisplayed()

        composeRule.onNodeWithText(INITIAL).performTextReplacement("Renamed")
        composeRule.runOnIdle { assertEquals("Renamed", state.text.toString()) }
        composeRule.onNodeWithText("Renamed").assertIsDisplayed()

        // Production callers reset the field through the state as well (SavedTextField.set).
        composeRule.runOnIdle { state.setTextAndPlaceCursorAtEnd(INITIAL) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(INITIAL).assertIsDisplayed()
    }

    @Test
    fun backReachesOnDismiss() {
        var dismissed = false
        show(onDismiss = { dismissed = true })

        composeRule.onNode(isDialog()).assertExists()
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertTrue("back should reach onDismissRequest", dismissed)
    }

    private fun hasErrorSemantics(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private fun show(
        state: TextFieldState = TextFieldState(INITIAL),
        error: String? = null,
        supportingText: String? = null,
        confirmLabel: String = okText,
        onDismiss: () -> Unit = {},
        onConfirm: () -> Unit = {}
    ) {
        composeRule.setContent {
            DreamDroidTheme {
                TextInputDialog(
                    title = TITLE,
                    state = state,
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                    error = error,
                    supportingText = supportingText,
                    confirmLabel = confirmLabel
                )
            }
        }
        composeRule.waitForIdle()
    }
}

private const val TITLE = "Add bouquet"
private const val INITIAL = "Sports"
private const val ERROR = "That name is taken"
private const val SUPPORTING = "Shown in the bouquet list"
