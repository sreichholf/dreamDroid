package net.reichholf.dreamdroid.ui.timers

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.TimerVps
import net.reichholf.dreamdroid.enigma.VpsMode
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.ui.compose.EDIT_PICK_CHEVRON_TAG
import net.reichholf.dreamdroid.ui.compose.saveAndDeleteActions
import net.reichholf.dreamdroid.ui.dialogs.MUTATION_PROGRESS_TAG
import net.reichholf.dreamdroid.ui.nav.LocalShellSnackbarHostState
import net.reichholf.dreamdroid.ui.nav.phoneNavDestinationViewport
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.LocalTvContentInset
import net.reichholf.dreamdroid.ui.theme.TvContentInset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
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
        val beginDate = composeRule.onNodeWithText("Begin date")
            .performScrollTo()
            .getBoundsInRoot()
        val beginTime = composeRule.onNodeWithText("Begin time")
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
        composeRule.onNodeWithText("Tags")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf(TimerEditPick.Tags), picks)
    }

    @Test
    fun pickRowKeepsItsTrailingChevron() {
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(sampleTimer()), showSaveFab = false)
            }
        }

        val tags = composeRule.onNodeWithText("Tags")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performScrollTo()
            .assertIsDisplayed()
        val row = tags.getBoundsInRoot()
        assertTrue(
            "Pick rows keep a 48.dp target, height=${row.height}",
            row.height >= 48.dp
        )

        val rowBounds = tags.fetchSemanticsNode().boundsInRoot
        val chevrons = composeRule.onAllNodesWithTag(EDIT_PICK_CHEVRON_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertTrue(
            "Pick rows end in a dropdown chevron, row=$rowBounds chevrons=$chevrons",
            chevrons.any { rowBounds.contains(it.center) }
        )
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

        val tags = composeRule.onNodeWithText("Tags")
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
    fun vpsFieldIsHiddenWithoutThePlugin() {
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(sampleTimer()))
            }
        }

        composeRule.onNodeWithContentDescription(string(R.string.vps)).assertDoesNotExist()
    }

    @Test
    fun vpsFieldShowsTheNoteOnlyWhenOn() {
        val actions = RecordingActions()
        val timer = sampleTimer().copy(eit = "4711", vps = TimerVps(VpsMode.Off))
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(timer), actions = actions)
            }
        }

        composeRule.onNodeWithContentDescription(string(R.string.vps))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.vps_note)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.vps)).performClick()
        composeRule.onNodeWithText(vpsModes()[2]).performClick()
        assertEquals(listOf(VpsMode.Overwrite), actions.modes)
    }

    @Test
    fun epgTimerWithVpsOnShowsTheNoteWithoutTimePickers() {
        val timer = sampleTimer().copy(eit = "4711", vps = TimerVps(VpsMode.Safe))
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(timer))
            }
        }

        composeRule.onNodeWithText(vpsModes()[1]).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.vps_note))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.vps_time)).assertDoesNotExist()
    }

    @Test
    fun manualTimerWithVpsOnShowsAndReportsTheVpsTimePickers() {
        val picks = mutableListOf<TimerEditPick>()
        val timer = sampleTimer().copy(eit = "", vps = TimerVps(VpsMode.Safe))
        composeRule.setContent {
            DreamDroidTheme {
                Form(form(timer), onPick = { picks += it })
            }
        }

        composeRule.onNodeWithText(string(R.string.vps_date))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(string(R.string.vps_time))
            .performScrollTo()
            .performClick()
        assertEquals(listOf(TimerEditPick.VpsDate, TimerEditPick.VpsTime), picks)
    }

    @Test
    fun saveErrorShowsInTheShellSnackbarOnceTheFormIsScrolled() {
        var shown = 0
        val actions = object : TimerFormActions {
            override fun onSaveErrorShown() {
                shown++
            }
        }
        var state by mutableStateOf(TimerEditUiState(timer = sampleTimer(), locations = LOCATIONS))
        val hostState = SnackbarHostState()
        composeRule.setContent {
            DreamDroidTheme {
                CompositionLocalProvider(LocalShellSnackbarHostState provides hostState) {
                    Box(Modifier.fillMaxSize()) {
                        TimerEditContent(
                            uiState = state,
                            name = remember { TextFieldState("Sample") },
                            description = remember { TextFieldState() },
                            actions = actions,
                            onPickService = {}
                        )
                        SnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        composeRule.onNodeWithText(string(R.string.repeatings)).performScrollTo()

        // The snackbar's timeout runs on the test clock.
        composeRule.mainClock.autoAdvance = false
        state = state.copy(saveError = UiText.Raw("Conflicting timer exists"))
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithText("Conflicting timer exists").assertIsDisplayed()
        assertEquals(0, shown)
        composeRule.mainClock.advanceTimeBy(SNACKBAR_GONE_MILLIS)
        assertEquals(1, shown)
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

        composeRule.onNodeWithText(string(R.string.repeatings))
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

    @Test
    fun tvFocusedRowKeepsItsFocusScalePastTheFormEdge() {
        lateinit var density: Density
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            density = LocalDensity.current
            inputModeManager = LocalInputModeManager.current
            // What the hub section pane does: its 32 dp margin is outside the form, and the
            // form counts it as its own, so full-width rows touch the form's clip.
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(TvWindow)) {
                DreamDroidTvTheme(fillBackground = false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .testTag("tv_form_root")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(HubInset)
                        ) {
                            CompositionLocalProvider(
                                LocalTvContentInset provides TvContentInset(HubInset, HubInset)
                            ) {
                                Form(form(sampleTimer()))
                            }
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }

        val location = composeRule.onNodeWithText("Location")
        location.requestFocus()
        composeRule.waitForIdle()
        // Let the focus scale and the scroll it asked for settle.
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        fun px(dp: Dp) = with(density) { dp.toPx() }
        val root = composeRule.onNodeWithTag("tv_form_root").fetchSemanticsNode().boundsInRoot
        val row = location.fetchSemanticsNode().boundsInRoot
        val image = composeRule.onNodeWithTag("tv_form_root").captureToImage().asAndroidBitmap()
        fun pixel(x: Float, y: Float) = image.getPixel(
            (x - root.left).toInt().coerceIn(0, image.width - 1),
            (y - root.top).toInt().coerceIn(0, image.height - 1)
        )

        // The host margin is outside the form; the row must start inside it.
        assertTrue(
            "the form margin is missing: row.left=${(row.left - root.left) / density.density}dp",
            row.left - root.left >= px(8.dp)
        )
        val y = row.center.y
        val fill = pixel(row.left + px(40.dp), y)
        // Just outside the row: inside its focus scale when the scale paints past the clip.
        val zoomed = pixel(row.left - px(4.dp), y)
        val formBackground = pixel(root.left + px(2.dp), y)
        assertTrue(
            "focused row clipped at the form edge: zoomed=#${Integer.toHexString(zoomed)} " +
                "fill=#${Integer.toHexString(fill)} " +
                "background=#${Integer.toHexString(formBackground)}",
            rgbDistance(zoomed, fill) < rgbDistance(zoomed, formBackground)
        )
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
        onPick: (TimerEditPick) -> Unit = {}
    ) {
        TimerEditScreen(
            form = form,
            name = name,
            description = remember { TextFieldState("Desc") },
            actions = actions,
            onPick = onPick,
            showSaveFab = showSaveFab
        )
    }

    private fun form(timer: Timer) = TimerEditForm.from(timer, LOCATIONS, vpsPlugin = true)

    private fun vpsModes(): Array<String> = InstrumentationRegistry.getInstrumentation()
        .targetContext.resources.getStringArray(R.array.vps_modes)

    private class RecordingActions : TimerFormActions {
        val modes = mutableListOf<VpsMode>()

        override fun onVpsModeChange(mode: VpsMode) {
            modes += mode
        }
    }

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

        /** Past a short snackbar's timeout, with room for an accessibility-longer one. */
        const val SNACKBAR_GONE_MILLIS = 15_000L
    }
}

/** A 1080p TV at its usual 2.0 density. */
private val TvWindow = DpSize(960.dp, 540.dp)

/** The hub section pane's screen margin, which sits outside a hosted form. */
private val HubInset = 32.dp

private fun rgbDistance(a: Int, b: Int): Int = abs(AndroidColor.red(a) - AndroidColor.red(b)) +
    abs(AndroidColor.green(a) - AndroidColor.green(b)) +
    abs(AndroidColor.blue(a) - AndroidColor.blue(b))
