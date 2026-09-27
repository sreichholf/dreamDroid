package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Material 3 shell top bar shows the bound destination's actions and the title. */
class ShellTopBarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun actionsClickByContentDescriptionAndTextActionsShowTheirLabel() {
        var tags = 0
        var search = 0
        val controller = ShellTopBarController()
        controller.title = "Movies"
        composeRule.setContent {
            hostShell(
                controller = controller,
                trailing = listOf(
                    ShellTopBarAction(
                        id = R.id.action_search,
                        label = "Search",
                        iconRes = R.drawable.ic_action_search,
                        onClick = { search += 1 }
                    )
                )
            ) {
                BindShellTopBarActions(
                    listOf(
                        ShellTopBarAction(
                            id = R.id.menu_tags,
                            label = "Tags",
                            iconRes = R.drawable.ic_action_tags,
                            onClick = { tags += 1 }
                        ),
                        ShellTopBarAction(
                            id = R.id.menu_save,
                            label = "Save",
                            enabled = false,
                            onClick = {}
                        )
                    )
                )
            }
        }
        composeRule.onNodeWithText("Movies").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tags").performClick()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Save").assertIsDisplayed().assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, tags)
            assertEquals(1, search)
        }
    }

    @Test
    fun successorDestinationReplacesActionsAndLeavingClearsThem() {
        composeRule.setContent {
            hostShell(controller = remember { ShellTopBarController() }) {
                var owner by remember { mutableIntStateOf(1) }
                when (owner) {
                    1 -> BindShellTopBarActions(listOf(action("Clean up")))
                    2 -> BindShellTopBarActions(listOf(action("Detect devices")))
                }
                Column {
                    Button(onClick = { owner = 2 }) { Text("swap") }
                    Button(onClick = { owner = 0 }) { Text("clear") }
                }
            }
        }
        composeRule.onNodeWithContentDescription("Clean up").assertIsDisplayed()
        composeRule.onNodeWithText("swap").performClick()
        composeRule.onNodeWithContentDescription("Detect devices").assertIsDisplayed()
        assertGone("Clean up")
        composeRule.onNodeWithText("clear").performClick()
        assertGone("Detect devices")
    }

    @Test
    fun navigationIconOpensTheDrawer() {
        var opened = 0
        composeRule.setContent {
            hostShell(
                controller = remember { ShellTopBarController() },
                onNavigationClick = { opened += 1 }
            ) {}
        }
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.drawer_open)
        ).performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    private fun assertGone(label: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun action(label: String) = ShellTopBarAction(
        id = label.hashCode(),
        label = label,
        iconRes = R.drawable.ic_action_clean,
        onClick = {}
    )
}

@Composable
private fun hostShell(
    controller: ShellTopBarController,
    trailing: List<ShellTopBarAction> = emptyList(),
    onNavigationClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    DreamDroidTheme {
        PhoneShell(
            drawerListState = remember { DrawerListState() },
            drawerOpen = false,
            onDrawerOpenChange = {},
            profileName = "Living Room",
            connectionLabel = "Online",
            onProfileClick = {},
            onDrawerItemClick = {},
            onNavigationClick = onNavigationClick,
            destinationController = remember { ShellDestinationBarController() },
            fabController = remember { ShellFabController() },
            topBarController = controller,
            trailingTopBarActions = trailing
        ) {
            content()
        }
    }
}
