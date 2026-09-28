package net.reichholf.dreamdroid.ui.timers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.ui.compose.saveAndDeleteActions
import net.reichholf.dreamdroid.ui.dialogs.MUTATION_PROGRESS_TAG
import net.reichholf.dreamdroid.ui.nav.phoneNavDestinationViewport
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TimerEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun createModeShowsKeyLabelsWithoutSaveFab() {
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(sampleTimer()), showSaveFab = false)
            }
        }

        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Zap").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Description").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(saveLabel()).assertDoesNotExist()

        val enabled = composeRule.onNode(hasText("Enabled") and isToggleable()).getBoundsInRoot()
        val zap = composeRule.onNode(hasText("Zap") and isToggleable()).getBoundsInRoot()
        assertTrue(
            "Enabled and Zap should stack, enabled=$enabled zap=$zap",
            zap.top >= enabled.bottom
        )
        val enabledHeight = enabled.bottom - enabled.top
        assertTrue(
            "Switch rows are at least 56.dp, height=$enabledHeight",
            enabledHeight >= 56.dp
        )
        val beginDate = composeRule.onNodeWithContentDescription("Begin date")
            .performScrollTo()
            .getBoundsInRoot()
        val beginTime = composeRule.onNodeWithContentDescription("Begin time")
            .getBoundsInRoot()
        assertTrue(
            "Begin date and time stay on one row, date=$beginDate time=$beginTime",
            kotlin.math.abs((beginDate.top - beginTime.top).value) < 8f
        )
    }

    @Test
    fun tagsFieldScrollsIntoViewAndReportsItsPick() {
        val picks = mutableListOf<TimerEditPick>()
        composeRule.setContent {
            DreamDroidTheme {
                Form(
                    form(sampleTimer().copy(tags = "News")),
                    showSaveFab = false,
                    onPick = { picks += it }
                )
            }
        }

        composeRule.onNodeWithText("News").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tags")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf(TimerEditPick.Tags), picks)
    }

    @Test
    fun tagsFieldClearsHostBottomInsetWithoutScaffoldFab() {
        composeRule.setContent {
            DreamDroidTheme {
                Box(Modifier.fillMaxSize().testTag("host")) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .phoneNavDestinationViewport(
                                shellBarVisible = false,
                                bottomInset = 48.dp
                            )
                    ) {
                        Form(form(sampleTimer().copy(tags = "News")), showSaveFab = false)
                    }
                }
            }
        }

        val tags = composeRule.onNodeWithContentDescription("Tags")
            .performScrollTo()
            .getBoundsInRoot()
        val host = composeRule.onNodeWithTag("host").getBoundsInRoot()
        assertTrue(
            "Tags must clear the host bottom inset, host=$host tags=$tags",
            tags.bottom <= host.bottom - 48.dp + 1.5.dp
        )
    }

    @Test
    fun editModeShowsTheTimerAndReportsToggles() {
        val timer = sampleTimer().copy(disabled = "1", justPlay = "1", repeated = "3")
        val enabledChanges = mutableListOf<Boolean>()
        val actions = object : TimerFormActions {
            override fun onEnabledChange(enabled: Boolean) {
                enabledChanges += enabled
            }
        }
        val name = TextFieldState("Tagesschau")
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(timer), name = name, actions = actions)
            }
        }

        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText(repeatedLabel(3)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enabled").performClick()
        assertEquals(listOf(true), enabledChanges)
    }

    @Test
    fun saveErrorShowsAboveTheForm() {
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(sampleTimer()), saveError = "Conflicting timer exists")
            }
        }

        composeRule.onNodeWithText("Conflicting timer exists").assertIsDisplayed()
    }

    @Test
    fun savingProgressShowsInContentWithoutDialog() {
        val state = TimerEditUiState(
            timer = sampleTimer(),
            locations = LOCATIONS,
            progress = UiText.Raw("Saving")
        )
        val name = TextFieldState("Sample")
        val description = TextFieldState()
        composeRule.setContent {
            DreamDroidTheme {
                TimerEditContent(
                    uiState = state,
                    name = name,
                    description = description,
                    actions = object : TimerFormActions {},
                    onPickService = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MUTATION_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Saving").assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun repeatedPickerReportsTheCheckedDays() {
        val picked = mutableListOf<List<Int>>()
        val actions = object : TimerFormActions {
            override fun onRepeatedChange(days: List<Int>) {
                picked += days
            }
        }
        val name = TextFieldState("Sample")
        val description = TextFieldState()
        composeRule.setContent {
            DreamDroidTheme {
                TimerEditContent(
                    uiState = TimerEditUiState(timer = sampleTimer(), locations = LOCATIONS),
                    name = name,
                    description = description,
                    actions = actions,
                    onPickService = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(string(R.string.repeatings))
            .performScrollTo()
            .performClick()
        composeRule.onNode(isDialog()).assertIsDisplayed()
        val monday = InstrumentationRegistry.getInstrumentation().targetContext
            .resources.getStringArray(R.array.weekdays)[0]
        composeRule.onNodeWithText(monday).performClick()
        composeRule.onNodeWithText(string(R.string.ok)).performClick()

        assertEquals(listOf(listOf(0)), picked)
    }

    @Test
    fun createTopBarOmitsDeleteAndEditTopBarIncludesIt() {
        val create = actions(canDelete = false)
        assertEquals(listOf(R.id.menu_save), create.map { it.id })

        val edit = actions(canDelete = true)
        assertEquals(listOf(R.id.menu_save, R.id.menu_delete), edit.map { it.id })
        assertTrue(edit.all { it.enabled })
    }

    private fun actions(canDelete: Boolean) = saveAndDeleteActions(
        saveLabel = "Save",
        deleteLabel = "Delete",
        canDelete = canDelete,
        onSave = {},
        onDelete = {}
    )

    @Composable
    private fun Form(
        form: TimerEditForm,
        name: TextFieldState = remember { TextFieldState("Sample") },
        actions: TimerFormActions = object : TimerFormActions {},
        showSaveFab: Boolean = true,
        saveError: String? = null,
        onPick: (TimerEditPick) -> Unit = {}
    ) {
        TimerEditScreen(
            form = form,
            name = name,
            description = remember { TextFieldState("Desc") },
            actions = actions,
            onPick = onPick,
            saveError = saveError,
            showSaveFab = showSaveFab
        )
    }

    private fun form(timer: Timer) = TimerEditForm.from(timer, LOCATIONS)

    private fun repeatedLabel(repeated: Int) = timerRepeatedLabel(
        InstrumentationRegistry.getInstrumentation().targetContext.resources,
        repeated
    )

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(id)

    private fun saveLabel() = string(R.string.save)

    private fun sampleTimer(): Timer = TimerHelper.getInitialTimer().copy(
        name = "Sample",
        description = "Desc",
        serviceName = "Das Erste HD",
        reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
        begin = "1893456000",
        end = "1893459600",
        disabled = "0",
        justPlay = "0",
        afterEvent = "3",
        location = "/hdd/movie/",
        repeated = "0",
        tags = ""
    )

    private companion object {
        val LOCATIONS = listOf("/hdd/movie/", "/media/hdd/")
    }
}
