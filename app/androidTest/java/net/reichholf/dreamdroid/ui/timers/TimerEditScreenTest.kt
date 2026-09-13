package net.reichholf.dreamdroid.ui.timers

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.nav.NavExtras
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
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun createModeShowsKeyLabelsAndSaveFab() {
        val state = TimerEditState().also {
            it.loadFrom(
                sampleTimer(),
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/", "/media/hdd/"),
                repeatedLabel = "None",
            )
        }
        var saveClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                TimerEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = { saveClicks++ },
                    onPickBeginDate = {},
                    onPickBeginTime = {},
                    onPickEndDate = {},
                    onPickEndTime = {},
                    onPickRepeated = {},
                    onPickService = {},
                    onPickTags = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Zap").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Description").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save").assertIsDisplayed().performClick()
        assertEquals(1, saveClicks)
    }

    @Test
    fun editModeSeedsFieldsAndToggles() {
        val timer = sampleTimer()
        timer.put(Timer.KEY_NAME, "Tagesschau")
        timer.put(Timer.KEY_DISABLED, "1")
        timer.put(Timer.KEY_JUST_PLAY, "1")
        val state = TimerEditState().also {
            it.loadFrom(
                timer,
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/"),
                repeatedLabel = "Mo, Tu",
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
    fun applyToWritesHashFields() {
        val timer = sampleTimer()
        val state = TimerEditState().also {
            it.loadFrom(
                timer,
                afterEvents = listOf("Nothing", "Standby", "Deep standby", "Auto"),
                locations = listOf("/hdd/movie/", "/media/hdd/"),
                repeatedLabel = "None",
            )
        }
        state.name = "Edited"
        state.description = "Desc"
        state.enabled = false
        state.zap = true
        state.afterEventIndex = 1
        state.locationIndex = 1
        state.applyTo(timer)
        assertEquals("Edited", timer.getString(Timer.KEY_NAME))
        assertEquals("Desc", timer.getString(Timer.KEY_DESCRIPTION))
        assertEquals("1", timer.getString(Timer.KEY_DISABLED))
        assertEquals("1", timer.getString(Timer.KEY_JUST_PLAY))
        assertEquals("1", timer.getString(Timer.KEY_AFTER_EVENT))
        assertEquals("/media/hdd/", timer.getString(Timer.KEY_LOCATION))
    }

    @Test
    fun servicePickReloadKeepsTypedTitleDescriptionAndToggles() {
        val session = sessionFrom(sampleTimer())
        session.reload()
        session.editState.name = "Keep This Title"
        session.editState.description = "Keep This Description"
        session.editState.enabled = false
        session.editState.zap = true

        val picked = ExtendedHashMap()
        picked.put(Service.KEY_NAME, "ZDF HD")
        picked.put(Service.KEY_REFERENCE, "1:0:1:6DCB:44D:1:C00000:0:0:0:")
        session.onActivityResult(
            Statics.REQUEST_PICK_SERVICE,
            Activity.RESULT_OK,
            Intent().putExtra(NavExtras.DATA, picked),
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

        val result = ExtendedHashMap()
        result.put(SimpleResult.KEY_STATE, Python.FALSE)
        result.put(SimpleResult.KEY_STATE_TEXT, "Conflicting timer exists")
        session.onSaveResult(result)
        composeRule.waitForIdle()

        assertNull(session.progress)
        composeRule.onNodeWithText("Conflicting timer exists").assertIsDisplayed()
    }

    private fun sessionFrom(timer: ExtendedHashMap): TimerEditSession {
        val session = TimerEditSession(
            routeTag = "timer_edit:new:1893456000",
            remountEpoch = 0,
            timer = timer,
            timerOld = null,
            isCreate = true,
            selectedTags = ArrayList(),
            checkedDays = BooleanArray(7),
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
            onPickTags = {},
        )
    }

    private fun sampleTimer(): ExtendedHashMap {
        val timer = Timer.getInitialTimer()
        timer.put(Timer.KEY_NAME, "Sample")
        timer.put(Timer.KEY_DESCRIPTION, "Desc")
        timer.put(Timer.KEY_SERVICE_NAME, "Das Erste HD")
        timer.put(Timer.KEY_REFERENCE, "1:0:1:6DCA:44D:1:C00000:0:0:0:")
        timer.put(Timer.KEY_BEGIN, "1893456000")
        timer.put(Timer.KEY_END, "1893459600")
        timer.put(Timer.KEY_DISABLED, "0")
        timer.put(Timer.KEY_JUST_PLAY, "0")
        timer.put(Timer.KEY_AFTER_EVENT, "3")
        timer.put(Timer.KEY_LOCATION, "/hdd/movie/")
        timer.put(Timer.KEY_REPEATED, "0")
        timer.put(Timer.KEY_TAGS, "")
        return timer
    }
}
