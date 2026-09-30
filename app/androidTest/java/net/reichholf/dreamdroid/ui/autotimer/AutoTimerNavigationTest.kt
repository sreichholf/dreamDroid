package net.reichholf.dreamdroid.ui.autotimer

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.ui.nav.AUTOTIMER_PICKED_TARGETS
import net.reichholf.dreamdroid.ui.nav.AutoTimerEdit
import net.reichholf.dreamdroid.ui.nav.AutoTimerPreview
import net.reichholf.dreamdroid.ui.nav.AutoTimerTargetPick
import net.reichholf.dreamdroid.ui.nav.AutoTimers
import net.reichholf.dreamdroid.ui.nav.deliverAutoTimerTargets
import net.reichholf.dreamdroid.ui.nav.showAutoTimerAfterSave
import org.junit.Rule
import org.junit.Test

/**
 * The AutoTimer back stack on a real NavHost: picks reach the editor through its entry's
 * `savedStateHandle`, and a save replaces the preview the editor came from.
 */
class AutoTimerNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    @Test
    fun picksReachTheEditorAndASaveReplacesTheStalePreview() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = AutoTimers) {
                composable<AutoTimers> { Text("list") }
                composable<AutoTimerPreview> { entry ->
                    val route = entry.toRoute<AutoTimerPreview>()
                    Text("preview ${route.id} ${route.name}")
                }
                composable<AutoTimerEdit> { entry ->
                    val picked by entry.savedStateHandle
                        .getStateFlow<ArrayList<Target>?>(AUTOTIMER_PICKED_TARGETS, null)
                        .collectAsStateWithLifecycle()
                    Text("picked: ${picked.orEmpty().joinToString { it.name }}")
                }
                composable<AutoTimerTargetPick> { Text("picker") }
            }
        }
        composeRule.runOnIdle {
            navController.navigate(AutoTimerPreview(1, "Wilsberg"))
            navController.navigate(AutoTimerEdit(1, "Wilsberg"))
            navController.navigate(AutoTimerTargetPick)
        }
        composeRule.onNodeWithText("picker").assertIsDisplayed()

        composeRule.runOnIdle {
            navController.deliverAutoTimerTargets(
                listOf(Target.Channel("1:0:19:2B66:3F3:1:C00000:0:0:0:", "ZDF HD"))
            )
        }
        composeRule.onNodeWithText("picked: ZDF HD").assertIsDisplayed()

        composeRule.runOnIdle { navController.showAutoTimerAfterSave(1, "Wilsberg neu") }
        composeRule.onNodeWithText("preview 1 Wilsberg neu").assertIsDisplayed()

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.onNodeWithText("list").assertIsDisplayed()
    }

    @Test
    fun aSaveFromTheListOpensThePreviewAboveIt() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = AutoTimers) {
                composable<AutoTimers> { Text("list") }
                composable<AutoTimerPreview> { entry ->
                    Text("preview ${entry.toRoute<AutoTimerPreview>().id}")
                }
                composable<AutoTimerEdit> { Text("editor") }
            }
        }
        composeRule.runOnIdle { navController.navigate(AutoTimerEdit()) }
        composeRule.onNodeWithText("editor").assertIsDisplayed()

        composeRule.runOnIdle { navController.showAutoTimerAfterSave(3, "Tatort") }
        composeRule.onNodeWithText("preview 3").assertIsDisplayed()

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.onNodeWithText("list").assertIsDisplayed()
    }
}
