package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneNavDestinationViewportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun leafViewportShrinksByBottomInsetWhenShellBarHidden() {
        composeRule.setContent {
            val insetPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            Box(Modifier.size(200.dp).testTag("host")) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .phoneNavDestinationViewport(
                            shellBarVisible = false,
                            bottomSafe = WindowInsets(bottom = insetPx)
                        )
                ) {
                    Box(Modifier.fillMaxSize().testTag("leaf"))
                }
            }
        }

        val host = composeRule.onNodeWithTag("host").getBoundsInRoot()
        val leaf = composeRule.onNodeWithTag("leaf").getBoundsInRoot()
        assertTrue(
            "Leaf must sit 48.dp above the host bottom, host=$host leaf=$leaf",
            abs((host.bottom - leaf.bottom - 48.dp).value) < 1.5f
        )
    }

    @Test
    fun hubViewportStaysFullWhenShellBarVisible() {
        composeRule.setContent {
            val insetPx = with(LocalDensity.current) { 48.dp.roundToPx() }
            Box(Modifier.size(200.dp).testTag("host")) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .phoneNavDestinationViewport(
                            shellBarVisible = true,
                            bottomSafe = WindowInsets(bottom = insetPx)
                        )
                ) {
                    Box(Modifier.fillMaxSize().testTag("leaf"))
                }
            }
        }

        val host = composeRule.onNodeWithTag("host").getBoundsInRoot()
        val leaf = composeRule.onNodeWithTag("leaf").getBoundsInRoot()
        assertTrue(
            "Hub leaves keep the overflow under the shell bar, host=$host leaf=$leaf",
            abs((host.bottom - leaf.bottom).value) < 1.5f
        )
    }
}
