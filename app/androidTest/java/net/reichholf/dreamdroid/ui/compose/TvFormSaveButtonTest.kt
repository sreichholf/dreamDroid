package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The TV form clips at the scroll viewport, and focus scrolling stops as soon as the focused
 * button's own bounds are visible. The Save pill's focus scale therefore needs room below it
 * that is scrolled in too, or its bottom edge is cut when it is selected.
 */
@OptIn(ExperimentalTestApi::class)
class TvFormSaveButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusedSaveButtonKeepsRoomForItsFocusScale() {
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            // A 1080p TV: the form is taller than the viewport, so focus must scroll.
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TvWindow)) {
                DreamDroidTvTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("viewport")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(600.dp))
                            TvFormSaveButton(label = "Save", onSave = {})
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }

        composeRule.onNodeWithText("Save").requestFocus()
        composeRule.waitForIdle()

        val save = composeRule.onNodeWithText("Save").fetchSemanticsNode().boundsInRoot
        val viewport = composeRule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        // The TV Button zooms to 1.1: it grows by 5% of its height on each side.
        val scaledGrowth = save.height * 0.05f
        assertTrue(
            "focused Save is cut at the viewport: $save inside $viewport",
            save.bottom + scaledGrowth <= viewport.bottom
        )
    }
}

/** A 1080p TV at its usual 2.0 density. */
private val TvWindow = DpSize(960.dp, 540.dp)
