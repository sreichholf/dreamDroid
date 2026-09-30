package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoTimerTargetPickScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Service>()
    private val toggled = mutableListOf<Service>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun aBouquetRowOpensAndItsCheckboxPicksTheWholeBouquet() {
        show(
            AutoTimerTargetPickUiState(
                rows = listOf(MARKER, BOUQUET),
                selected = listOf(Target.Bouquet(BOUQUET.reference, BOUQUET.name))
            )
        )

        composeRule.onNodeWithText("Section").assertExists()
        composeRule.onNodeWithContentDescription("Whole bouquet Favourites").assertIsOn()
            .performClick()
        composeRule.onNodeWithText("Favourites").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(BOUQUET), toggled)
            assertEquals(listOf(BOUQUET), opened)
        }
    }

    @Test
    fun aChannelRowIsPickedWithATap() {
        show(
            AutoTimerTargetPickUiState(
                bouquet = Target.Bouquet(BOUQUET.reference, BOUQUET.name),
                rows = listOf(CHANNEL)
            )
        )

        composeRule.onNodeWithText("Das Erste HD").assertIsOff().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(CHANNEL), toggled)
            assertEquals(emptyList<Service>(), opened)
        }
    }

    private fun show(state: AutoTimerTargetPickUiState) {
        composeRule.setContent {
            DreamDroidTheme {
                AutoTimerTargetPickScreen(
                    state = state,
                    onRefresh = {},
                    onOpen = { opened += it },
                    onToggle = { toggled += it }
                )
            }
        }
    }

    private companion object {
        val MARKER = Service("1:64:0:0:0:0:0:0:0:0::Section", "Section")
        val BOUQUET = Service(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            "Favourites"
        )
        val CHANNEL = Service("1:0:1:6DCA:44D:1:C00000:0:0:0:", "Das Erste HD")
    }
}
