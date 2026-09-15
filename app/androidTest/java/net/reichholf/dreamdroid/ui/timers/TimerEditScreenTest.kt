package net.reichholf.dreamdroid.ui.timers

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.phoneNavDestinationViewport
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun createModeShowsKeyLabelsAndSaveFab() {
        val state = TimerEditState().also {
            it.loadFrom(
                sampleTimer(),
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/", "/media/hdd/"),
                repeatedLabel = "None"
            )
        }
        composeRule.setContent {
            DreamDroidTheme {
                TimerEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                    onPickBeginDate = {},
                    onPickBeginTime = {},
                    onPickEndDate = {},
                    onPickEndTime = {},
                    onPickRepeated = {},
                    onPickService = {},
                    onPickTags = {},
                    showSaveFab = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Zap").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Description").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").performScrollTo().assertIsDisplayed()

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
    fun tagsFieldScrollsIntoViewAndOpensPickerWithoutScaffoldFab() {
        val state = TimerEditState().also {
            it.loadFrom(
                sampleTimer().copy(tags = "News"),
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/", "/media/hdd/"),
                repeatedLabel = "None"
            )
        }
        var tagPicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                TimerEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                    onPickBeginDate = {},
                    onPickBeginTime = {},
                    onPickEndDate = {},
                    onPickEndTime = {},
                    onPickRepeated = {},
                    onPickService = {},
                    onPickTags = { tagPicks++ },
                    showSaveFab = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("Save").assertDoesNotExist()
        composeRule.onNodeWithText("News").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tags")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, tagPicks)
    }

    @Test
    fun tagsFieldClearsHostBottomInsetWithoutScaffoldFab() {
        val state = TimerEditState().also {
            it.loadFrom(
                sampleTimer().copy(tags = "News"),
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/", "/media/hdd/"),
                repeatedLabel = "None"
            )
        }
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
                        TimerEditScreen(
                            state = state,
                            saveLabel = "Save",
                            onSave = {},
                            onPickBeginDate = {},
                            onPickBeginTime = {},
                            onPickEndDate = {},
                            onPickEndTime = {},
                            onPickRepeated = {},
                            onPickService = {},
                            onPickTags = {},
                            showSaveFab = false
                        )
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
    fun editModeSeedsFieldsAndToggles() {
        val timer = sampleTimer().copy(
            name = "Tagesschau",
            disabled = "1",
            justPlay = "1"
        )
        val state = TimerEditState().also {
            it.loadFrom(
                timer,
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/"),
                repeatedLabel = "Mo, Tu"
            )
        }
        composeRule.setContent {
            DreamDroidTheme {
                TimerEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                    onPickBeginDate = {},
                    onPickBeginTime = {},
                    onPickEndDate = {},
                    onPickEndTime = {},
                    onPickRepeated = {},
                    onPickService = {},
                    onPickTags = {}
                )
            }
        }

        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Mo, Tu").performScrollTo().assertIsDisplayed()
        assertTrue(!state.enabled)
        assertTrue(state.zap)
        composeRule.onNodeWithText("Enabled").performClick()
        assertTrue(state.enabled)
    }

    @Test
    fun applyToWritesTimerFields() {
        val timer = sampleTimer()
        val state = TimerEditState().also {
            it.loadFrom(
                timer,
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/", "/media/hdd/"),
                repeatedLabel = "None"
            )
        }
        state.name = "Edited"
        state.description = "Desc"
        state.enabled = false
        state.zap = true
        state.afterEventIndex = 1
        state.locationIndex = 1
        val updated = state.applyTo(timer)
        assertEquals("Edited", updated.name)
        assertEquals("Desc", updated.description)
        assertEquals("1", updated.disabled)
        assertEquals("1", updated.justPlay)
        assertEquals("1", updated.afterEvent)
        assertEquals("/media/hdd/", updated.location)
    }

    @Test
    fun servicePickReloadKeepsTypedTitleDescriptionAndToggles() {
        val session = sessionFrom(sampleTimer())
        session.reload()
        session.editState.name = "Keep This Title"
        session.editState.description = "Keep This Description"
        session.editState.enabled = false
        session.editState.zap = true

        val picked = Service("1:0:1:6DCB:44D:1:C00000:0:0:0:", "ZDF HD")
        session.onActivityResult(
            Statics.REQUEST_PICK_SERVICE,
            Activity.RESULT_OK,
            Intent().putExtra(NavExtras.DATA, picked)
        )
        session.reload()

        assertEquals("Keep This Title", session.editState.name)
        assertEquals("Keep This Description", session.editState.description)
        assertEquals("ZDF HD", session.editState.serviceName)
        assertTrue(!session.editState.enabled)
        assertTrue(session.editState.zap)
    }

    @Test
    fun failedSaveShowsBoxErrorAfterSpinnerClears() {
        val session = sessionFrom(sampleTimer())
        session.reload()
        session.progress = IndeterminateProgressState(message = "Saving")
        composeRule.setContent {
            DreamDroidTheme {
                timerEditForm(session.editState)
            }
        }

        session.onSaveResult(
            SimpleResult(state = Python.FALSE, stateText = "Conflicting timer exists")
        )
        composeRule.waitForIdle()

        assertNull(session.progress)
        composeRule.onNodeWithText("Conflicting timer exists").assertIsDisplayed()
    }

    private fun sessionFrom(timer: Timer): TimerEditSession {
        val session = TimerEditSession(
            routeTag = "timer_edit:new:1893456000",
            remountEpoch = 0,
            timer = timer,
            timerOld = null,
            isCreate = true,
            selectedTags = ArrayList(),
            checkedDays = BooleanArray(7)
        )
        session.context = InstrumentationRegistry.getInstrumentation().targetContext
        return session
    }

    @Composable
    private fun timerEditForm(state: TimerEditState) {
        TimerEditScreen(
            state = state,
            saveLabel = "Save",
            onSave = {},
            onPickBeginDate = {},
            onPickBeginTime = {},
            onPickEndDate = {},
            onPickEndTime = {},
            onPickRepeated = {},
            onPickService = {},
            onPickTags = {}
        )
    }

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
}
