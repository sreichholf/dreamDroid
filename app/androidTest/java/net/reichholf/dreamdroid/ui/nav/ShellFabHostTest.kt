package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The shell FAB is shared across destinations. Successor binds must win over a
 * stale dispose (NavHost / load remount flicker). The label is merged into the
 * button content description, so that is what these tests assert.
 */
class ShellFabHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun remountKeepsLabeledFabVisible() {
        composeRule.setContent {
            hostShell {
                var remount by remember { mutableIntStateOf(0) }
                key(remount) {
                    BindShellFab(
                        contentDescription = "Add Profile",
                        iconRes = R.drawable.ic_action_fab_add,
                        onClick = {},
                        text = "Add Profile"
                    )
                }
                Column {
                    Button(onClick = { remount += 1 }) { Text("remount") }
                }
            }
        }
        composeRule.onNodeWithContentDescription("Add Profile").assertIsDisplayed()
        composeRule.onNodeWithText("remount").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Add Profile").assertIsDisplayed()
    }

    @Test
    fun successorBindKeepsFabVisibleAndUpdatesLabel() {
        composeRule.setContent {
            hostShell {
                var owner by remember { mutableIntStateOf(1) }
                val label = if (owner == 1) "Add Profile" else "New timer"
                if (owner != 0) {
                    key(owner) {
                        BindShellFab(
                            contentDescription = label,
                            iconRes = R.drawable.ic_action_fab_add,
                            onClick = {},
                            text = label
                        )
                    }
                }
                Column {
                    Button(onClick = { owner = 2 }) { Text("swap") }
                    Button(onClick = { owner = 0 }) { Text("clear") }
                }
            }
        }
        composeRule.onNodeWithContentDescription("Add Profile").assertIsDisplayed()
        composeRule.onNodeWithText("swap").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("New timer").assertIsDisplayed()
        composeRule.onNodeWithText("clear").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("New timer")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}

@Composable
private fun hostShell(content: @Composable () -> Unit) {
    val destination = remember { ShellDestinationBarController() }
    val fab = remember { ShellFabController() }
    DreamDroidTheme {
        PhoneShell(
            drawerListState = remember { DrawerListState() },
            drawerOpen = false,
            onDrawerOpenChange = {},
            profileName = "Living Room",
            connectionLabel = "Online",
            onProfileClick = {},
            onDrawerItemClick = {},
            onNavigationClick = {},
            destinationController = destination,
            fabController = fab,
            onToolbarReady = {}
        ) {
            content()
        }
    }
}
