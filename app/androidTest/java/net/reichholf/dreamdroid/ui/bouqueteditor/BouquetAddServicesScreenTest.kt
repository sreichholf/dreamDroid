package net.reichholf.dreamdroid.ui.bouqueteditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.BouquetMode
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetEntryKind
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BouquetAddServicesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sources = mutableListOf<ServiceSource>()
    private val folders = mutableListOf<BouquetEntry>()
    private val toggled = mutableListOf<BouquetEntry>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun offersTheSources() {
        show(STATE)

        composeRule.onNodeWithText("Satellites").assertIsDisplayed()
        composeRule.onNodeWithText("Providers").assertIsDisplayed()
        composeRule.onNodeWithText("All services").performClick()

        composeRule.runOnIdle { assertEquals(listOf(ServiceSource.All), sources) }
    }

    @Test
    fun foldersOpenAndServicesToggle() {
        show(services(selected = setOf(ARTE.reference)))

        composeRule.onNodeWithText("19.2 O - Kanäle").performClick()
        service("arte HD").assertIsOn()
        service("phoenix HD").assertIsOff().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(SATELLITE), folders)
            assertEquals(listOf(PHOENIX), toggled)
        }
    }

    @Test
    fun serviceTheBouquetHasIsCheckedAndDisabled() {
        show(services(present = setOf(PHOENIX.reference)))

        composeRule.onNodeWithText("Already in the bouquet").assertIsDisplayed()
        service("phoenix HD").assertIsOn().assertIsNotEnabled()
    }

    @Test
    fun failureShowsItsOwnMessage() {
        show(
            STATE.copy(
                source = ServiceSource.Providers,
                content = AddServicesList.Failed(UiText.Raw("Timeout"))
            )
        )

        composeRule.onNodeWithText("Timeout").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").assertIsDisplayed()
    }

    private fun service(name: String) =
        composeRule.onNode(isToggleable() and hasText(name, substring = true))

    private fun show(state: BouquetAddServicesUiState) {
        composeRule.setContent {
            DreamDroidTheme {
                BouquetAddServicesScreen(
                    state = state,
                    onSource = { sources += it },
                    onFolder = { folders += it },
                    onToggle = { toggled += it },
                    onRetry = {}
                )
            }
        }
    }

    private fun services(selected: Set<String> = emptySet(), present: Set<String> = emptySet()) =
        STATE.copy(
            source = ServiceSource.Satellites,
            content = AddServicesList.Ready(listOf(SATELLITE, ARTE, PHOENIX)),
            selected = selected,
            present = present
        )

    private companion object {
        val STATE = BouquetAddServicesUiState(
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.fav.tv\" ORDER BY bouquet",
            mode = BouquetMode.Tv
        )
        val SATELLITE = BouquetEntry(
            "1:7:0:0:0:0:C00000:0:0:0:(satellitePosition == 192) ORDER BY name:19.2 O - Kanäle",
            "19.2 O - Kanäle",
            BouquetEntryKind.Directory
        )
        val ARTE = BouquetEntry(
            "1:0:19:283E:3FB:1:C00000:0:0:0:",
            "arte HD",
            BouquetEntryKind.Service
        )
        val PHOENIX = BouquetEntry(
            "1:0:19:2887:40F:1:C00000:0:0:0:",
            "phoenix HD",
            BouquetEntryKind.Service
        )
    }
}
