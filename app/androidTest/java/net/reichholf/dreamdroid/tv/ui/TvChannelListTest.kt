package net.reichholf.dreamdroid.tv.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvChannelListTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val marker = ServiceNowNext("1:64:1:0:0:0:0:0:0:0::News", "News")
    private val spacer = ServiceNowNext("1:832:d:0:0:0:0:0:0:0:", "")
    private val daserste = channel("Das Erste HD", "1:0:19:283D:3FB:1:C00000:0:0:0:", "Tagesschau")
    private val zdf = channel("ZDF HD", "1:0:19:2B66:3F3:1:C00000:0:0:0:", "heute journal")
    private val arte = channel("arte HD", "1:0:19:283E:3FB:1:C00000:0:0:0:", "Arte Journal")
    private val services = listOf(marker, daserste, spacer, zdf, arte)
    private val bouquets = listOf(
        Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"tv\"", "Favourites"),
        Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"hd\"", "HD"),
        Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"radio\"", "Radio")
    )
    private val playingTag =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.tv_now_playing).uppercase()

    @Before
    fun forceAlwaysNight() {
        setThemeType("1")
    }

    @After
    fun restoreAlwaysNight() {
        forceAlwaysNight()
    }

    @Test
    fun rowsShowNamesAndNowTitlesWithoutNumbers() {
        setList()
        composeRule.onNodeWithTag(TV_CHANNEL_LIST_TAG).assertIsDisplayed()
        for (service in listOf(daserste, zdf, arte)) {
            row(service.serviceName).assertIsDisplayed()
                .assert(hasText(service.now!!.title))
        }
        val rows = composeRule.onAllNodesWithTag(TV_CHANNEL_ROW_TAG).fetchSemanticsNodes()
        assertEquals("marker and spacer are no channel rows", 3, rows.size)
        val texts = rows.flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }
        assertTrue("no channel numbers: $texts", texts.none { Regex("^\\d").containsMatchIn(it) })
    }

    @Test
    fun playingRowIsSelectedAndTagged() {
        setList()
        row(zdf.serviceName).assertIsSelected().assert(hasText(playingTag))
        row(daserste.serviceName).assertIsNotSelected()
        composeRule.onAllNodesWithTag(TV_CHANNEL_ROW_TAG)
            .fetchSemanticsNodes()
            .count { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty()
                    .any { it.text == playingTag }
            }
            .let { assertEquals(1, it) }
    }

    @Test
    fun focusRequesterLandsOnPlayingRow() {
        val requester = FocusRequester()
        setList(focusRequester = requester)
        composeRule.runOnIdle { requester.requestFocus() }
        awaitFocused(row(zdf.serviceName))
    }

    @Test
    fun focusRequesterLandsOnFirstChannelWhenPlayingIsNotListed() {
        val requester = FocusRequester()
        setList(currentRef = "1:0:1:DEAD:1:1:C00000:0:0:0:", focusRequester = requester)
        composeRule.runOnIdle { requester.requestFocus() }
        awaitFocused(row(daserste.serviceName))
    }

    @Test
    fun okOnRowCallsOnServiceClick() {
        var clicked: ServiceNowNext? = null
        val requester = FocusRequester()
        setList(focusRequester = requester, onServiceClick = { clicked = it })
        composeRule.runOnIdle { requester.requestFocus() }
        val node = row(zdf.serviceName)
        awaitFocused(node)
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitUntil { clicked != null }
        assertEquals(zdf, clicked)
    }

    @Test
    fun leftAndRightSwitchBouquetsWithWrapAround() {
        var selected by mutableStateOf(bouquets.first().reference)
        val picked = mutableListOf<Service>()
        val requester = FocusRequester()
        setList(
            selectedBouquetRef = { selected },
            focusRequester = requester,
            onSelectBouquet = {
                picked += it
                selected = it.reference
            }
        )
        composeRule.onNodeWithText("Favourites").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
        composeRule.runOnIdle { requester.requestFocus() }
        val node = row(zdf.serviceName)
        awaitFocused(node)

        node.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitUntil { picked.size == 1 }
        assertEquals(bouquets[2], picked[0])
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()

        row(zdf.serviceName).performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitUntil { picked.size == 2 }
        assertEquals(bouquets[0], picked[1])

        row(zdf.serviceName).performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitUntil { picked.size == 3 }
        assertEquals(bouquets[1], picked[2])
        row(zdf.serviceName).assertIsFocused()
    }

    @Test
    fun heldKeyRepeatSwitchesBouquetsOnce() {
        var selected by mutableStateOf(bouquets.first().reference)
        val picked = mutableListOf<Service>()
        val requester = FocusRequester()
        setList(
            selectedBouquetRef = { selected },
            focusRequester = requester,
            onSelectBouquet = {
                picked += it
                selected = it.reference
            }
        )
        composeRule.runOnIdle { requester.requestFocus() }
        awaitFocused(row(zdf.serviceName))

        sendDpad(KeyEvent.ACTION_DOWN, repeat = 0)
        composeRule.waitUntil { picked.size == 1 }
        assertEquals(bouquets[1], picked[0])
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()

        sendDpad(KeyEvent.ACTION_DOWN, repeat = 1)
        sendDpad(KeyEvent.ACTION_DOWN, repeat = 2)
        sendDpad(KeyEvent.ACTION_UP, repeat = 0)
        composeRule.waitForIdle()
        assertEquals("a held key switches once", 1, picked.size)
        composeRule.onNodeWithText("2 / 3").assertIsDisplayed()

        sendDpad(KeyEvent.ACTION_DOWN, repeat = 0)
        composeRule.waitUntil { picked.size == 2 }
        assertEquals(bouquets[2], picked[1])
    }

    @Test
    fun singleBouquetConsumesLeftAndRight() {
        val picked = mutableListOf<Service>()
        val requester = FocusRequester()
        var interactions = 0
        setList(
            bouquets = bouquets.take(1),
            focusRequester = requester,
            onSelectBouquet = { picked += it },
            onUserInteraction = { interactions++ }
        )
        composeRule.runOnIdle { requester.requestFocus() }
        val node = row(zdf.serviceName)
        awaitFocused(node)
        val before = composeRule.runOnIdle { interactions }
        node.performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionLeft)
        }
        composeRule.waitUntil { interactions >= before + 4 }
        assertTrue(picked.isEmpty())
        node.assertIsFocused()
    }

    @Test
    fun markersAreNotFocusable() {
        val requester = FocusRequester()
        setList(currentRef = daserste.serviceReference, focusRequester = requester)
        val markerNode = composeRule.onNodeWithText(marker.serviceName, useUnmergedTree = true)
        markerNode.assertIsDisplayed().assertHasNoClickAction()
        composeRule.runOnIdle { requester.requestFocus() }
        val first = row(daserste.serviceName)
        awaitFocused(first)
        first.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        markerNode.assert(isNotFocusable())
        first.assertIsFocused()
    }

    @Test
    fun upAndDownMoveFocusAndDetailFollows() {
        val requester = FocusRequester()
        setList(focusRequester = requester)
        composeRule.onNodeWithTag(TV_CHANNEL_DETAIL_TAG).assertDoesNotExist()
        composeRule.runOnIdle { requester.requestFocus() }
        awaitFocused(row(zdf.serviceName))
        detail().assert(hasAnyDescendant(hasText(zdf.serviceName)))
            .assert(hasAnyDescendant(hasText(zdf.now!!.title)))
            .assert(hasAnyDescendant(hasText(zdf.now!!.description)))
            .assert(
                hasAnyDescendant(hasText("${zdf.next!!.startTimeReadable}  ${zdf.next!!.title}"))
            )

        row(zdf.serviceName).performKeyInput { pressKey(Key.DirectionDown) }
        awaitFocused(row(arte.serviceName))
        detail().assert(hasAnyDescendant(hasText(arte.now!!.title)))

        row(arte.serviceName).performKeyInput { pressKey(Key.DirectionUp) }
        awaitFocused(row(zdf.serviceName))
        row(zdf.serviceName).performKeyInput { pressKey(Key.DirectionUp) }
        awaitFocused(row(daserste.serviceName))
        detail().assert(hasAnyDescendant(hasText(daserste.now!!.title)))
    }

    @Test
    fun epgRefreshKeepsFocus() {
        var list by mutableStateOf(services)
        val requester = FocusRequester()
        setList(services = { list }, focusRequester = requester)
        composeRule.runOnIdle { requester.requestFocus() }
        awaitFocused(row(zdf.serviceName))
        row(zdf.serviceName).performKeyInput { pressKey(Key.DirectionDown) }
        awaitFocused(row(arte.serviceName))
        composeRule.runOnIdle {
            list = services.map { service ->
                service.copy(now = service.now?.let { it.copy(title = it.title + " (live)") })
            }
        }
        detail().let { node ->
            composeRule.waitUntil {
                runCatching { node.assert(hasAnyDescendant(hasText("Arte Journal (live)"))) }
                    .isSuccess
            }
        }
        row(arte.serviceName).assertIsFocused()
    }

    @Test
    fun keyInputAndFocusCallOnUserInteraction() {
        var interactions = 0
        val requester = FocusRequester()
        setList(focusRequester = requester, onUserInteraction = { interactions++ })
        composeRule.runOnIdle { requester.requestFocus() }
        awaitFocused(row(zdf.serviceName))
        val afterFocus = composeRule.runOnIdle { interactions }
        assertTrue("focus resets the auto-hide timer", afterFocus > 0)
        row(zdf.serviceName).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil { interactions > afterFocus }
    }

    @Test
    fun nowBarsFollowTheDeviceClock() {
        val startSec = 1_700_000_000L
        val running = ServiceNowNext(
            serviceReference = daserste.serviceReference,
            serviceName = daserste.serviceName,
            // The receiver's freeze-frame clock: one minute in, and it stays there.
            now = Event(
                title = daserste.now!!.title,
                start = startSec.toString(),
                duration = "3600",
                currentTime = (startSec + 60).toString()
            )
        )
        var nowMs by mutableStateOf(startSec * 1000)
        val requester = FocusRequester()
        setList(
            services = { listOf(running) },
            currentRef = running.serviceReference,
            nowMs = { nowMs },
            focusRequester = requester
        )
        composeRule.runOnIdle { requester.requestFocus() }
        awaitFocused(row(running.serviceName))
        assertEquals(0f, rowProgress(running.serviceName), 0.001f)
        assertEquals(0f, detailProgress(), 0.001f)

        composeRule.runOnIdle { nowMs += 30 * 60_000L }
        composeRule.waitUntil { rowProgress(running.serviceName) > 0.49f }
        assertEquals(0.5f, rowProgress(running.serviceName), 0.001f)
        assertEquals(0.5f, detailProgress(), 0.001f)
    }

    @Test
    fun binderBridgesViewFocusIntoPlayingRow() {
        val view = setBoundView()
        assertTrue(view.isFocusable)
        assertTrue(view.isFocusableInTouchMode)
        composeRule.runOnIdle { view.requestFocus() }
        awaitFocused(row(zdf.serviceName))
    }

    @Test
    fun panelStaysDarkWithLightAppTheme() {
        setThemeType("0")
        setBoundView()
        val bitmap = composeRule.onNodeWithTag(TV_CHANNEL_LIST_TAG)
            .assertIsDisplayed()
            .captureToImage()
            .asAndroidBitmap()
        val x = bitmap.width / 2
        val y = (bitmap.height * 0.95f).toInt().coerceIn(0, bitmap.height - 1)
        val pixel = Color(bitmap.getPixel(x, y))
        assertTrue(
            "player channel list forces dark; argb=#${Integer.toHexString(pixel.toArgb())}",
            pixel.luminance() < 0.4f
        )
    }

    private fun setList(
        services: () -> List<ServiceNowNext> = { this.services },
        currentRef: String? = zdf.serviceReference,
        bouquets: List<Service> = this.bouquets,
        selectedBouquetRef: () -> String? = { bouquets.first().reference },
        nowMs: () -> Long = { System.currentTimeMillis() },
        focusRequester: FocusRequester? = null,
        onServiceClick: (ServiceNowNext) -> Unit = {},
        onSelectBouquet: (Service) -> Unit = {},
        onUserInteraction: (() -> Unit)? = null
    ) {
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            // The detail card sits beside the 400dp panel: a TV window, not the phone emulator.
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(TvWindow)) {
                DreamDroidTvTheme(fillBackground = false, forceDark = true) {
                    TvChannelList(
                        services = services(),
                        currentRef = currentRef,
                        bouquets = bouquets,
                        selectedBouquetRef = selectedBouquetRef(),
                        onServiceClick = onServiceClick,
                        onSelectBouquet = onSelectBouquet,
                        nowMs = nowMs(),
                        focusRequester = focusRequester,
                        onUserInteraction = onUserInteraction
                    )
                }
            }
        }
        requestKeyboardMode(inputModeManager)
    }

    private fun setBoundView(): ComposeView {
        val state = VideoOverlayUiState().apply {
            zapServices = services
            zapCurrentRef = zdf.serviceReference
            this.bouquets = this@TvChannelListTest.bouquets
            selectedBouquetRef = this@TvChannelListTest.bouquets.first().reference
        }
        lateinit var inputModeManager: InputModeManager
        var composeView: ComposeView? = null
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            AndroidView(
                factory = { ctx ->
                    ComposeView(ctx).also { view ->
                        composeView = view
                        view.bindTvChannelList(state, onServiceClick = {}, onSelectBouquet = {})
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        requestKeyboardMode(inputModeManager)
        composeRule.onNodeWithTag(TV_CHANNEL_LIST_TAG).assertExists()
        return composeView!!
    }

    // Clickables take D-pad focus only outside touch mode (docs/tv-focus-testing.md).
    private fun requestKeyboardMode(inputModeManager: InputModeManager) {
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
    }

    private fun row(name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasTestTag(TV_CHANNEL_ROW_TAG) and hasText(name))

    private fun detail(): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(TV_CHANNEL_DETAIL_TAG).assertIsDisplayed()

    private fun rowProgress(name: String): Float {
        val info = row(name).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        checkNotNull(info) { "the $name row has no progress bar" }
        return info.current
    }

    private fun detailProgress(): Float = composeRule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
            hasAnyAncestor(hasTestTag(TV_CHANNEL_DETAIL_TAG)),
        useUnmergedTree = true
    ).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    // The Compose test API cannot set a repeat count, which is what a held D-pad key sends.
    private fun sendDpad(action: Int, repeat: Int) {
        val event = KeyEvent(0L, 0L, action, KeyEvent.KEYCODE_DPAD_RIGHT, repeat)
        composeRule.runOnIdle {
            assertTrue(
                "the list did not take the key event",
                composeRule.activity.window.decorView.dispatchKeyEvent(event)
            )
        }
    }

    private fun awaitFocused(node: SemanticsNodeInteraction) {
        composeRule.waitUntil { runCatching { node.assertIsFocused() }.isSuccess }
    }

    private fun setThemeType(type: String) {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, type)
            .putBoolean(DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS, false)
            .commit()
    }

    private fun channel(name: String, reference: String, nowTitle: String) = ServiceNowNext(
        serviceReference = reference,
        serviceName = name,
        now = Event(title = nowTitle, description = "$nowTitle description"),
        next = Event(title = "$name next", startTimeReadable = "20:15")
    )
}

private val TvWindow = DpSize(960.dp, 540.dp)
