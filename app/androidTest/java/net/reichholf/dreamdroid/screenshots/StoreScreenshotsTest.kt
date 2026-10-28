package net.reichholf.dreamdroid.screenshots

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo.Companion.Indeterminate
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import net.reichholf.dreamdroid.tv.activities.MainActivity as TvMainActivity
import net.reichholf.dreamdroid.ui.compose.LIST_DETAIL_DETAIL_PANE_TAG
import net.reichholf.dreamdroid.ui.compose.LIST_DETAIL_EXTRA_PANE_TAG
import net.reichholf.dreamdroid.ui.nav.Current
import net.reichholf.dreamdroid.ui.nav.Remote
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The store screenshots (`fastlane/metadata/android/en-US/images/`), taken from the real app
 * against [ScreenshotReceiver] with the fake picon. The instrumentation argument
 * `screenshots` picks the device flow: `phone` (phoneScreenshots), `tablet`
 * (tenInchScreenshots) or `tv` (tvScreenshots); without it every flow skips. The
 * `screenshots` input of android-ci.yml's workflow_dispatch runs each on its emulator and
 * uploads the PNGs. A flow's whole folder is published, so the TV form chrome, which
 * developers review but which is not a store asset, has a flow of its own (`tv_review`)
 * and its own folder.
 */
@HiltAndroidTest
class StoreScreenshotsTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    lateinit var settings: SettingsRepository

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val receiver = ScreenshotReceiver(System.currentTimeMillis() / 1000)
    private val device = InstrumentationRegistry.getArguments().getString("screenshots")
    private val loading = hasText(context.getString(R.string.loading))
    private val first = ScreenshotReceiver.TV_CHANNELS[0]
    private val second = ScreenshotReceiver.TV_CHANNELS[1]
    private val kids = ScreenshotReceiver.TV_CHANNELS[6]
    private val favourites = ScreenshotReceiver.FAVOURITES
    private lateinit var server: MockWebServer
    private lateinit var outDir: File
    private var profileId: Int = 0

    @Before
    fun setUp() {
        assumeTrue(device != null)
        hiltRule.inject()
        server = MockWebServer().apply {
            dispatcher = receiver
            start()
        }
        outDir = File(context.filesDir, "screenshots/$device").apply {
            deleteRecursively()
            mkdirs()
        }
        writeFakePicon()
        settings.update {
            it.copy(picons = true, fakePicon = true, instantZap = false, themeType = "1")
        }
        settings.lastVersionCode = BuildConfig.VERSION_CODE
        settings.firstStart = false
        val profile = profiles.newProfile().apply {
            name = "Living room"
            host = server.hostName
            port = server.port
            ssl = false
        }
        runBlocking {
            profiles.save(profile)
            profileId = checkNotNull(profile.id)
            profiles.setCurrent(profileId)
        }
        profiles.markLoaded()
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    @Test
    fun phone() {
        assumeTrue(device == "phone")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitServiceList()
            capture("1_services")
            captureTimersAndMovies("5_timers", "6_movies")

            clickText(context.getString(R.string.tv))
            awaitServiceList()
            openEpgFromRowMenu()
            // The hub's bouquet tabs go with the hub; only then is a title an EPG row.
            awaitGone(hasText(ScreenshotReceiver.DOCUMENTARY.name))
            awaitGone(loading)
            val later = receiver.titleLater(second)
            awaitNode(hasText(later) and hasClickAction())
            capture("3_epg")

            val list = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            composeRule.onAllNodes(hasText(later) and hasClickAction())[0].performClick()
            awaitSettled(hasText(context.getString(R.string.set_timer)) and hasClickAction())
            // The sheet is its own window; its semantics settle before it is on screen.
            awaitScreenChange(list)
            capture("4_event")

            captureMultiEpg(scenario, "2_multiepg")

            scenario.onActivity { it.phoneNav.navigateToRoute(Current) }
            awaitGone(hasText(context.getString(R.string.epg_at_this_time)))
            awaitGone(loading)
            awaitNode(hasText(receiver.titleNow(first)))
            capture("8_current")

            scenario.onActivity { it.phoneNav.navigateToRoute(Remote) }
            awaitGone(hasText(receiver.titleNow(first)))
            composeRule.waitForIdle()
            capture("7_remote")
        }
    }

    /**
     * Landscape, where the window is large (1200dp and up): the service list, a channel's
     * EPG and one of its events side by side.
     */
    @Test
    fun tablet() {
        assumeTrue(device == "tablet")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            awaitServiceList()
            captureTimersAndMovies("3_timers", "4_movies", openDetails = true)

            clickText(context.getString(R.string.tv))
            awaitServiceList()
            openEpgFromRowMenu()
            val later = receiver.titleLater(second)
            val event = hasText(later) and hasClickAction() and inPane(LIST_DETAIL_DETAIL_PANE_TAG)
            awaitNode(event)
            composeRule.onAllNodes(event)[0].performClick()
            awaitNode(hasTestTag(LIST_DETAIL_EXTRA_PANE_TAG))
            awaitSettled(hasText(context.getString(R.string.set_timer)) and hasClickAction())
            capture("1_panes")

            captureMultiEpg(scenario, "2_multiepg")
        }
    }

    /**
     * The TV hub: a bouquet's channels, its MultiEPG, the timers and the recordings. Four
     * store assets, so the form editors are a flow of their own ([tvReview]).
     */
    @Test
    fun tv() {
        assumeTrue(device == "tv")
        ActivityScenario.launch(TvMainActivity::class.java).use { scenario ->
            awaitNode(hasTestTag("hub_service_card"))
            awaitNode(hasText(second.name))
            capture("1_hub")

            // TV surfaces answer D-pad and semantic clicks, not touch. Guide, Timers and
            // Recordings are top bar tabs; a movie location is an item of the Recordings drawer.
            tvClick("hub_tab_guide")
            awaitNode(hasTestTag("tv_multi_epg_grid"))
            // The channel row, not a programme title: titles turn over with the clock, so a
            // wait on receiver.titleNow() can miss the cell the grid shows after a boundary.
            awaitNode(hasTestTag("tv_multi_epg_channel_${first.ref}#0"))
            capture("2_multiepg")

            tvClick("hub_tab_timers")
            awaitNode(hasText(receiver.timerTitles().first()))
            capture("3_timers")

            tvClick("hub_tab_recordings")
            tvClick("hub_header_/media/hdd/movie/")
            awaitNode(hasTestTag("hub_movie_card"))
            awaitNode(hasText(ScreenshotReceiver.MOVIE_TITLES.first()))
            capture("4_movies")
        }
    }

    /**
     * The TV form chrome for review, not a store asset: the timer and profile editors.
     * `-e screenshots tv_review -e class …StoreScreenshotsTest#tvReview`.
     */
    @Test
    fun tvReview() {
        assumeTrue(device == "tv_review")
        ActivityScenario.launch(TvMainActivity::class.java).use { captureTvEditors(it) }
    }

    private fun captureTvEditors(scenario: ActivityScenario<TvMainActivity>) {
        // New timer: the editor replaces the timers section, Back returns to the list.
        tvClick("hub_tab_timers")
        awaitNode(hasText(receiver.timerTitles().first()))
        // Gate on the screen, not on semantics: the display can lag the composition by a
        // frame, and two stale frames of the list look stable to saveScreen().
        val timersList = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        tvClick("tv_timers_add")
        awaitNode(hasText(context.getString(R.string.save)) and hasClickAction())
        awaitScreenChange(timersList)
        capture("1_timer_editor")
        back(scenario)
        awaitNode(hasTestTag("tv_timers_list"))

        // Settings → Profile → edit the seeded profile.
        tvClick("hub_action_settings")
        tvClick("hub_settings_profile")
        awaitNode(hasTestTag("tv_profiles_list"))
        tvClick("tv_profiles_edit_$profileId")
        awaitNode(hasText(context.getString(R.string.profile)))
        capture("2_profile_editor")

        // The form's other end: the last section's controls and the Save button.
        composeRule.onAllNodes(hasText(context.getString(R.string.save)))[0].performScrollTo()
        capture("3_profile_editor_save")
    }

    /** Compose's BackHandler runs through the activity's dispatcher, so this is a real Back. */
    private fun back(scenario: ActivityScenario<TvMainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun tvClick(tag: String) {
        awaitNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** The hub's TV tab once its list loaded: the second channel only shows as a row. */
    private fun awaitServiceList() {
        awaitGone(loading)
        awaitNode(row(second.name))
        awaitNode(hasText(receiver.titleNow(second)))
    }

    /**
     * The timer and movie lists. With [openDetails] (two panes), a timer's editor and a
     * recording's info fill the detail pane beside them.
     */
    private fun captureTimersAndMovies(
        timers: String,
        movies: String,
        openDetails: Boolean = false
    ) {
        clickText(context.getString(R.string.timer))
        awaitGone(hasText(ScreenshotReceiver.DOCUMENTARY.name))
        awaitGone(loading)
        awaitNode(hasText(receiver.timerTitles().first()))
        if (openDetails) {
            // The second timer waits; the first one is recording.
            val timer = receiver.timerTitles()[1]
            composeRule.onAllNodes(listRow(timer))[0].performClick()
            awaitNode(hasText(timer) and inPane(LIST_DETAIL_DETAIL_PANE_TAG))
        }
        capture(timers)

        clickText(context.getString(R.string.movies))
        awaitGone(loading)
        awaitNode(hasText(ScreenshotReceiver.MOVIE_TITLES.first()))
        if (openDetails) {
            val movie = ScreenshotReceiver.MOVIE_TITLES[1]
            composeRule.onAllNodes(listRow(movie))[0].performClick()
            // The row menu's Info entry is labelled EPG, as is the drawer's EPG item.
            val info = hasText(context.getString(R.string.epg)) and hasClickAction() and
                hasAnyAncestor(isPopup())
            awaitNode(info)
            composeRule.onAllNodes(info)[0].performClick()
            awaitNode(hasText(movie) and inPane(LIST_DETAIL_DETAIL_PANE_TAG))
        }
        capture(movies)
    }

    private fun listRow(text: String) = hasText(text) and hasClickAction() and
        !inPane(LIST_DETAIL_DETAIL_PANE_TAG)

    private fun openEpgFromRowMenu() {
        composeRule.onNode(row(second.name)).performClick()
        clickText(context.getString(R.string.browse_epg))
    }

    private fun captureMultiEpg(scenario: ActivityScenario<MainActivity>, name: String) {
        scenario.onActivity { it.phoneNav.navigateToMultiEpg(favourites.ref, favourites.name) }
        awaitNode(hasText(context.getString(R.string.epg_at_this_time)))
        awaitGone(loading)
        // KiKA's shows run on no other channel, so this is a MultiEPG cell.
        awaitNode(hasText(receiver.titleNow(kids)))
        capture(name)
    }

    private fun row(channel: String) = hasText(channel) and hasClickAction() and
        !inPane(LIST_DETAIL_DETAIL_PANE_TAG) and !inPane(LIST_DETAIL_EXTRA_PANE_TAG)

    private fun inPane(tag: String) = hasAnyAncestor(hasTestTag(tag))

    /** The app's logo on a dark tile, at picon size, as `picon_default.png`. */
    private fun writeFakePicon() {
        val logo =
            checkNotNull(ContextCompat.getDrawable(context, R.drawable.dreamdroid_logo_on_dark))
        val bitmap = Bitmap.createBitmap(PICON_WIDTH, PICON_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0x26, 0x32, 0x38))
        val size = PICON_HEIGHT * 3 / 2
        val left = (PICON_WIDTH - size) / 2
        val top = (PICON_HEIGHT - size) / 2
        logo.setBounds(left, top, left + size, top + size)
        logo.draw(canvas)
        val png = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        val dir = File(Picon.localDir(context)).apply { mkdirs() }
        File(dir, "picon_default.png").writeBytes(png)
        receiver.piconPng = png
    }

    private fun clickText(text: String) {
        val node = hasText(text) and hasClickAction()
        awaitNode(node)
        composeRule.onAllNodes(node)[0].performClick()
    }

    /** On a timeout, leaves the screen, its semantics and the receiver's requests in [outDir]. */
    private fun awaitNode(matcher: SemanticsMatcher) {
        try {
            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            saveScreen("failure")
            val tree = runCatching {
                composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n") {
                    composeRule.onAllNodes(isRoot())[it].printToString(Int.MAX_VALUE)
                }
            }.getOrElse { it.toString() }
            File(outDir, "failure.txt").writeText(
                "Waiting for: ${matcher.description}\n\n$tree\n\nRequests:\n" +
                    synchronized(receiver.log) { receiver.log.joinToString("\n") }
            )
            throw e
        }
    }

    /**
     * Waits until [matcher]'s node is inside the window and stopped moving, as a sheet's
     * content is once the sheet is open: before that it is composed below the screen.
     */
    private fun awaitSettled(matcher: SemanticsMatcher) {
        awaitNode(matcher)
        val height = context.resources.displayMetrics.heightPixels
        var last = Float.NaN
        composeRule.waitUntil(TIMEOUT_MS) {
            val bounds = composeRule.onAllNodes(matcher).fetchSemanticsNodes()
                .firstOrNull()?.boundsInRoot
            val top = bounds?.top ?: Float.NaN
            val onScreen = bounds != null && bounds.bottom <= height
            (onScreen && top == last).also { last = top }
        }
    }

    /**
     * Waits until the bottom quarter of the screen, where a sheet's actions sit, no longer
     * matches [before]. The tapped row, near the top, does not reach it.
     */
    private fun awaitScreenChange(before: Bitmap) {
        fun bottom(b: Bitmap) = Bitmap.createBitmap(b, 0, b.height * 3 / 4, b.width, b.height / 4)
        val old = bottom(before)
        composeRule.waitUntil(TIMEOUT_MS) {
            val now = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
            !bottom(now).sameAs(old)
        }
    }

    private fun awaitGone(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Waits out a "Loading…" title and progress indicators, then saves the screen. */
    private fun capture(name: String) {
        awaitGone(hasText(context.getString(R.string.loading)))
        awaitGone(SemanticsMatcher.expectValue(ProgressBarRangeInfo, Indeterminate))
        composeRule.waitForIdle()
        saveScreen(name)
    }

    /**
     * Saves the screen once two screenshots in a row agree: the display lags the
     * composition by a frame or more, so the first one after idle can still be stale. A
     * screen that keeps moving (a TV card's marquee) is saved as it is after [STABLE_MS], with
     * a note beside it.
     */
    private fun saveScreen(name: String) {
        var last: Bitmap? = null
        var shot: Bitmap? = null
        try {
            composeRule.waitUntil(STABLE_MS) {
                val now = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
                val stable = last?.sameAs(now) == true
                last = now
                shot = now
                stable
            }
        } catch (e: ComposeTimeoutException) {
            File(outDir, "$name.unstable.txt").writeText(e.toString())
        }
        File(outDir, "$name.png").outputStream().use {
            checkNotNull(shot).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val STABLE_MS = 5_000L
        const val PICON_WIDTH = 220
        const val PICON_HEIGHT = 132
    }
}
