package net.reichholf.dreamdroid.ui.epg

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.EventListLoadResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.ui.nav.PhoneNavHostState
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.navigateToServiceEpg
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServiceEpgRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun popBackFromEventDetailKeepsListWithoutReload() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        val handle = PhoneNavHostState(app, SavedStateHandle())
        val loads = AtomicInteger(0)
        var loadParams: List<NameValuePair> = emptyList()
        val hooks = ServiceEpgLoadHooks(
            profileId = { null },
            shouldSkipReceiverHttp = { false },
            loadEvents = { _, params ->
                loads.incrementAndGet()
                loadParams = params
                EventListLoadResult(true, listOf(tagesschau()), null)
            }
        )
        val viewModels = mutableListOf<ServiceEpgViewModel>()
        lateinit var navController: NavHostController
        composeRule.setContent {
            DreamDroidTheme {
                navController = rememberNavController()
                NavHost(navController = navController, startDestination = PhoneNavRoutes.HUB) {
                    composable(PhoneNavRoutes.HUB) { Text("Hub") }
                    composable(
                        route = PhoneNavRoutes.SERVICE_EPG,
                        arguments = listOf(
                            navArgument(PhoneNavRoutes.ARG_SERVICE_REF) {
                                type = NavType.StringType
                            },
                            navArgument(PhoneNavRoutes.ARG_SERVICE_NAME) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) {
                        val viewModel: ServiceEpgViewModel = viewModel()
                        viewModel.loadHooks = hooks
                        viewModels += viewModel
                        ServiceEpgDestination(handle = handle, viewModel = viewModel)
                    }
                    composable(
                        route = PhoneNavRoutes.EPG_SEARCH,
                        arguments = listOf(
                            navArgument(PhoneNavRoutes.ARG_QUERY) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) {
                        Text("Search")
                    }
                }
            }
        }
        composeRule.runOnIdle {
            handle.attachNavController(navController)
            navController.navigateToServiceEpg(SERVICE_REF, "Das Erste HD")
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(app.getString(R.string.similar)).performClick()
        composeRule.onNodeWithText("Search").assertIsDisplayed()

        composeRule.runOnIdle { handle.popNavBackStack() }

        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("pop back must not start a fresh load", 1, loads.get())
            assertEquals(
                listOf("sRef" to SERVICE_REF),
                loadParams.map { it.key() to it.value() }
            )
            assertSame(viewModels.first(), viewModels.last())
        }
    }

    private fun tagesschau() = Event(
        eventId = "100",
        title = "Tagesschau",
        startReadable = "20:00",
        durationReadable = "15",
        descriptionExtended = "Die Nachrichten."
    )

    private companion object {
        const val SERVICE_REF = "1:0:19:283D:3FB:1:C00000:0:0:0:"
    }
}
