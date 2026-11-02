package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileBouquetsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun listsTvAndRadioWithOneSwitchPerBouquet() {
        show(
            ProfileBouquetsUiState(
                profileName = "Living Room",
                tv = listOf(FAVOURITES, SPORTS),
                radio = listOf(RADIO),
                hidden = setOf(SPORTS.reference)
            )
        )

        composeRule.onNodeWithText("Living Room").assertIsDisplayed()
        composeRule.onNodeWithText("TV").assertIsDisplayed()
        composeRule.onNodeWithText("Radio").assertIsDisplayed()
        composeRule.onNodeWithTag(rowTag(FAVOURITES)).assertIsOn()
        composeRule.onNodeWithTag(rowTag(SPORTS)).assertIsOff()
        composeRule.onNodeWithTag(rowTag(RADIO)).assertIsOn()
        composeRule.onNodeWithText("Hidden").assertIsDisplayed()
    }

    @Test
    fun tappingAHiddenBouquetShowsItAgain() {
        val toggled = mutableListOf<Pair<Service, Boolean>>()
        show(
            ProfileBouquetsUiState(
                tv = listOf(FAVOURITES, SPORTS),
                hidden = setOf(SPORTS.reference)
            ),
            onToggle = { service, visible -> toggled += service to visible }
        )

        composeRule.onNodeWithTag(rowTag(SPORTS)).performClick()

        composeRule.runOnIdle { assertEquals(listOf(SPORTS to true), toggled) }
    }

    @Test
    fun showAllIsHiddenWhenNothingIsHidden() {
        show(ProfileBouquetsUiState(tv = listOf(FAVOURITES)))

        composeRule.onNodeWithTag(BOUQUET_VISIBILITY_SHOW_ALL_TAG).assertDoesNotExist()
    }

    @Test
    fun showAllAppearsWhileSomethingIsHidden() {
        var shown = 0
        show(
            ProfileBouquetsUiState(
                tv = listOf(FAVOURITES),
                hidden = setOf(FAVOURITES.reference)
            ),
            onShowAll = { shown++ }
        )

        composeRule.onNodeWithTag(BOUQUET_VISIBILITY_SHOW_ALL_TAG)
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, shown) }
    }

    @Test
    fun failureOffersAReload() {
        var reloads = 0
        show(
            ProfileBouquetsUiState(
                loading = false,
                error = UiText.Raw("Timeout")
            ),
            onReload = { reloads++ }
        )

        composeRule.onNodeWithText("Timeout").assertIsDisplayed()
        composeRule.onNodeWithText("Reload").performClick()

        composeRule.runOnIdle { assertEquals(1, reloads) }
    }

    @Test
    fun anEmptyReceiverSaysSo() {
        show(ProfileBouquetsUiState(loading = false))

        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getString(R.string.no_bouquets_on_receiver)
        ).assertIsDisplayed()
    }

    private fun show(
        state: ProfileBouquetsUiState,
        onToggle: (Service, Boolean) -> Unit = { _, _ -> },
        onShowAll: () -> Unit = {},
        onReload: () -> Unit = {}
    ) {
        composeRule.setContent {
            DreamDroidTheme {
                ProfileBouquetsScreen(
                    state = state,
                    onToggle = onToggle,
                    onShowAll = onShowAll,
                    onReload = onReload
                )
            }
        }
    }

    private fun rowTag(service: Service): String =
        BOUQUET_VISIBILITY_ROW_TAG_PREFIX + service.reference

    private companion object {
        val FAVOURITES = Service("favourites", "Favourites")
        val SPORTS = Service("sports", "Sports")
        val RADIO = Service("radio", "Radio One")
    }
}
