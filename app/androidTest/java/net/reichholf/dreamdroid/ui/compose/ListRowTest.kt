package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ListRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun aClickableRowIsOneNodeWithItsLabelAndClicks() {
        var clicks = 0
        var longClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                ListRow(
                    headlineContent = { Text("Das Erste HD") },
                    supportingContent = { Text("Tagesschau") },
                    onClick = { clicks++ },
                    onLongClick = { longClicks++ }
                )
            }
        }

        val row = composeRule.onNode(
            hasTestTag(LIST_ROW_TAG) and hasText("Das Erste HD") and hasText("Tagesschau") and
                hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
        )
        row.performClick()
        row.performTouchInput { longClick() }

        composeRule.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, longClicks)
        }
    }

    @Test
    fun aDisabledRowKeepsItsClickButIsNotEnabled() {
        composeRule.setContent {
            DreamDroidTheme {
                ListRow(
                    headlineContent = { Text("Bedroom") },
                    onClick = {},
                    enabled = false
                )
            }
        }

        composeRule.onNode(hasText("Bedroom") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun aRowWithoutOnClickHasNoClickAction() {
        composeRule.setContent {
            DreamDroidTheme {
                ListRow(headlineContent = { Text("Unreadable") })
            }
        }

        composeRule.onNode(hasText("Unreadable") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun aToggleableRowCarriesItsStateWithItsLabel() {
        composeRule.setContent {
            DreamDroidTheme {
                ListRow(
                    headlineContent = { Text("Include passwords") },
                    modifier = Modifier.toggleable(
                        value = true,
                        role = Role.Switch,
                        onValueChange = {}
                    )
                )
            }
        }

        composeRule.onNode(
            hasTestTag(LIST_ROW_TAG) and hasText("Include passwords") and isToggleable()
        ).assertIsOn()
    }

    @Test
    fun eachRowIsOneTileInsideTheGutter() {
        composeRule.setContent {
            DreamDroidTheme {
                Column {
                    ListRow(headlineContent = { Text("One") }, modifier = Modifier.clickable {})
                    ListRow(headlineContent = { Text("Two") }, modifier = Modifier.clickable {})
                }
            }
        }

        val tiles = composeRule.onAllNodesWithTag(LIST_ROW_TAG)
        tiles.assertCountEquals(2)
        tiles[0].assertLeftPositionInRootIsEqualTo(ListRowHorizontalInset)
        val first = tiles[0].getBoundsInRoot()
        val second = tiles[1].getBoundsInRoot()
        assertEquals(ListRowVerticalInset * 2, second.top - first.bottom)
    }
}
