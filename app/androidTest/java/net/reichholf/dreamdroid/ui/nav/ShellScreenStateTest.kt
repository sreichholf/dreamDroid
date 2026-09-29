package net.reichholf.dreamdroid.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A destination reports its UI-state title and user message to the phone shell. */
class ShellScreenStateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun titleAndUserMessageReachTheShell() {
        var message by mutableStateOf<UiText?>(UiText.Resource(R.string.auth_error))
        var shown = 0
        composeRule.setContent {
            DreamDroidTheme {
                PhoneShell(
                    drawerListState = remember { DrawerListState() },
                    drawerOpen = false,
                    onDrawerOpenChange = {},
                    profileName = "Living Room",
                    connectionLabel = "Online",
                    boxActionsBlocked = false,
                    onProfileClick = {},
                    onDrawerItemClick = {},
                    onNavigationClick = {},
                    destinationController = remember { ShellDestinationBarController() },
                    fabController = remember { ShellFabController() },
                    topBarController = remember { ShellTopBarController() }
                ) {
                    ShellTitle(UiText.Resource(R.string.device_info))
                    ShowShellUserMessage(message) {
                        shown += 1
                        message = null
                    }
                }
            }
        }
        val authError = composeRule.activity.getString(R.string.auth_error)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.device_info))
            .assertIsDisplayed()
        composeRule.onNodeWithText(authError).assertIsDisplayed()
        // Past SnackbarDuration.Short: onShown runs once the snackbar is dismissed.
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitUntil(timeoutMillis = 15_000) { shown == 1 }
        composeRule.runOnIdle { assertEquals(1, shown) }
    }
}
