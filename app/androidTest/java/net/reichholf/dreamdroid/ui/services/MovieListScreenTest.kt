package net.reichholf.dreamdroid.ui.services

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MovieListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsMovieTitleAndService() {
        composeRule.setContent {
            DreamDroidTheme {
                MovieListScreen(
                    items = listOf(
                        MovieListItem(
                            index = 0,
                            title = "Recorded film",
                            serviceName = "ZDF",
                            fileSize = "1.2 GB",
                            time = "01.01.2026",
                            length = "90"
                        )
                    ),
                    onItemClick = {},
                    onItemLongClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Recorded film", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(24.dp)
        composeRule.onNodeWithText("ZDF").assertIsDisplayed()
    }

    @Test
    fun aRowCarriesItsClicksWithItsTitle() {
        val clicked = mutableListOf<Int>()
        val longClicked = mutableListOf<Int>()
        composeRule.setContent {
            DreamDroidTheme {
                MovieListScreen(
                    items = listOf(
                        MovieListItem(
                            index = 0,
                            title = "Recorded film",
                            serviceName = "ZDF",
                            fileSize = "1.2 GB",
                            time = "01.01.2026",
                            length = "90"
                        )
                    ),
                    onItemClick = { clicked += it.index },
                    onItemLongClick = { longClicked += it.index }
                )
            }
        }
        val row = composeRule.onNode(
            hasText("Recorded film") and hasClickAction() and
                SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
        )
        row.performClick()
        row.performTouchInput { longClick() }

        composeRule.runOnIdle {
            assertEquals(listOf(0), clicked)
            assertEquals(listOf(0), longClicked)
        }
    }

    @Test
    fun theRecordingShownBesideTheListIsSelected() {
        fun movie(index: Int, title: String, reference: String) = MovieListItem(
            index = index,
            title = title,
            serviceName = "ZDF",
            fileSize = "1.2 GB",
            time = "01.01.2026",
            length = "90",
            reference = reference
        )
        composeRule.setContent {
            DreamDroidTheme {
                MovieListScreen(
                    items = listOf(
                        movie(0, "First film", "1:0:0:0:0:0:0:0:0:0:/hdd/movie/a.ts"),
                        movie(1, "Second film", "1:0:0:0:0:0:0:0:0:0:/hdd/movie/b.ts"),
                        movie(2, "Unknown film", "")
                    ),
                    onItemClick = {},
                    onItemLongClick = {},
                    selectedReference = "1:0:0:0:0:0:0:0:0:0:/hdd/movie/b.ts"
                )
            }
        }
        composeRule.onNode(
            hasText("Second film") and hasClickAction()
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNode(
            hasText("First film") and hasClickAction()
        ).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        composeRule.onNode(
            hasText("Unknown film") and hasClickAction()
        ).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }
}
