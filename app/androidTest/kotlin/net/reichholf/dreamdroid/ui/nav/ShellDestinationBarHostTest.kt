package net.reichholf.dreamdroid.ui.nav

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.tools.ToolsDestination
import net.reichholf.dreamdroid.ui.tools.ToolsHubState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Shell destination bar chrome is owned by the NavHost host, not hub leaves.
 * Leaves only publish Snapshot state; clearing must not wipe a successor's publish.
 */
class ShellDestinationBarHostTest {
	@get:Rule
	val composeRule = createComposeRule()

	@Before
	fun forceAlwaysNight() {
		PreferenceManager.getDefaultSharedPreferences(
			InstrumentationRegistry.getInstrumentation().targetContext,
		).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
	}

	@Test
	fun registerPublishesToolsStateToController() {
		val state = ToolsHubState().apply { selected = ToolsDestination.SIGNAL }
		lateinit var controller: ShellDestinationBarController
		composeRule.setContent {
			DreamDroidTheme {
				ProvideShellDestinationBarForTest { c ->
					controller = c
					RegisterShellDestinationBar(ShellDestinationBarContent.Tools(state))
					Text("hub body")
				}
			}
		}
		composeRule.onNodeWithText("hub body").assertIsDisplayed()
		composeRule.runOnIdle {
			val content = controller.content
			assertTrue(content is ShellDestinationBarContent.Tools)
			assertEquals(
				ToolsDestination.SIGNAL,
				(content as ShellDestinationBarContent.Tools).state.selected,
			)
		}
	}

	@Test
	fun disposeClearsOnlyWhenStillOwner() {
		val first = ToolsHubState().apply { selected = ToolsDestination.SCREENSHOT }
		val second = ToolsHubState().apply { selected = ToolsDestination.DEVICE_INFO }
		lateinit var controller: ShellDestinationBarController
		composeRule.setContent {
			DreamDroidTheme {
				var showFirst by remember { mutableStateOf(true) }
				ProvideShellDestinationBarForTest { c ->
					controller = c
					if (showFirst) {
						RegisterShellDestinationBar(ShellDestinationBarContent.Tools(first))
					} else {
						RegisterShellDestinationBar(ShellDestinationBarContent.Tools(second))
					}
					Text(if (showFirst) "first" else "second")
				}
				// Flip after first frame via side channel Text click substitute:
				androidx.compose.foundation.layout.Box {
					if (showFirst) {
						androidx.compose.material3.Button(onClick = { showFirst = false }) {
							Text("swap")
						}
					}
				}
			}
		}
		composeRule.onNodeWithText("first").assertIsDisplayed()
		composeRule.runOnIdle {
			assertEquals(
				ToolsDestination.SCREENSHOT,
				(controller.content as ShellDestinationBarContent.Tools).state.selected,
			)
		}
		composeRule.onNodeWithText("swap").performClick()
		composeRule.onNodeWithText("second").assertIsDisplayed()
		composeRule.runOnIdle {
			assertEquals(
				ToolsDestination.DEVICE_INFO,
				(controller.content as ShellDestinationBarContent.Tools).state.selected,
			)
		}
	}
}

/**
 * Test double for [ProvideShellDestinationBar] that exposes the controller without
 * requiring the activity [R.id.shell_destination_nav] ComposeView.
 */
@Composable
private fun ProvideShellDestinationBarForTest(
	content: @Composable (ShellDestinationBarController) -> Unit,
) {
	val controller = remember { ShellDestinationBarController() }
	CompositionLocalProvider(LocalShellDestinationBarController provides controller) {
		content(controller)
	}
}
