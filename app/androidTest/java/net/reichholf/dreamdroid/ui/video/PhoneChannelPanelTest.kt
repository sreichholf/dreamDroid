package net.reichholf.dreamdroid.ui.video

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PhoneChannelPanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker = ServiceNowNext("1:64:1:0:0:0:0:0:0:0::Regional", "Regional")
    private val spacer = ServiceNowNext("1:832:d:0:0:0:0:0:0:0:", "")
    private val daserste = channel("Das Erste HD", "1:0:19:283D:3FB:1:C00000:0:0:0:", "Tagesschau")
    private val zdf = channel("ZDF HD", "1:0:19:2B66:3F3:1:C00000:0:0:0:", "Volle Kanne")
    private val swr = channel("SWR BW HD", "1:0:19:2B5C:3F3:1:C00000:0:0:0:", "Kaffee oder Tee")
    private val services = listOf(daserste, zdf, spacer, marker, swr)
    private val bouquets = listOf(
        Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"tv\"", "Favourites"),
        Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"hd\"", "HD"),
        Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"radio\"", "Radio")
    )
    private val playingTag = context.getString(R.string.tv_now_playing).uppercase()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS, false)
            .commit()
    }

    @Test
    fun headerNamesTheBouquetAndItsPosition() {
        setPanel()
        composeRule.onNode(isHeading() and hasText("Favourites")).assertIsDisplayed()
            .assert(hasText("1 / 3"))
    }

    @Test
    fun chevronsSwitchBouquetsAndWrapAround() {
        val picked = mutableListOf<String>()
        setPanel(onSelectBouquet = { picked += it.name })
        composeRule.onNodeWithContentDescription(context.getString(R.string.next_bouquet))
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.previous_bouquet))
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf("HD", "Radio"), picked) }
    }

    @Test
    fun horizontalSwipeOnThePanelSwitchesBouquets() {
        val picked = mutableListOf<String>()
        setPanel(onSelectBouquet = { picked += it.name })
        composeRule.onNodeWithTag(PHONE_CHANNEL_PANEL_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(PHONE_CHANNEL_PANEL_TAG).performTouchInput { swipeRight() }
        composeRule.runOnIdle { assertEquals(listOf("HD", "Radio"), picked) }
    }

    @Test
    fun anUnlistedBouquetSwitchesToTheFirstOrLastAndHasNoPosition() {
        val picked = mutableListOf<String>()
        setPanel(selectedBouquetRef = { "1:7:1:provider" }, onSelectBouquet = { picked += it.name })
        composeRule.onNode(isHeading() and hasText(context.getString(R.string.bouquets)))
            .assertIsDisplayed()
        composeRule.onNode(hasText("/", substring = true)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.next_bouquet))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.previous_bouquet))
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf("Favourites", "Radio"), picked) }
    }

    @Test
    fun switchingToABouquetWithoutThePlayingChannelStartsAtTheTop() {
        val many = (1..40).map { n ->
            channel("Channel $n", "1:0:1:$n:1:1:C00000:0:0:0:", "Show $n")
        }
        var selected by mutableStateOf(bouquets.first().reference)
        var rows by mutableStateOf(many)
        setPanel(
            services = { rows },
            currentRef = { many[30].serviceReference },
            selectedBouquetRef = { selected }
        )
        row("Channel 31").assertIsDisplayed()
        composeRule.runOnIdle {
            rows = many.reversed().drop(15)
            selected = bouquets[1].reference
        }
        row("Channel 25").assertIsDisplayed()
    }

    @Test
    fun aSingleBouquetHasNoChevrons() {
        setPanel(bouquets = bouquets.take(1))
        composeRule.onNodeWithContentDescription(context.getString(R.string.next_bouquet))
            .assertDoesNotExist()
        composeRule.onNode(isHeading() and hasText("Favourites")).assertIsDisplayed()
    }

    @Test
    fun playingRowIsSelectedAndTagged() {
        setPanel()
        row(zdf.serviceName).assertIsSelected().assert(hasText(playingTag))
            .assert(hasText(zdf.now!!.title))
        row(daserste.serviceName).assertIsNotSelected()
    }

    @Test
    fun tappingAChannelHandsItOver() {
        var clicked: ServiceNowNext? = null
        setPanel(onServiceClick = { clicked = it })
        row(daserste.serviceName).performClick()
        composeRule.runOnIdle { assertEquals(daserste, clicked) }
    }

    @Test
    fun markersAreHeadingsAndNoRows() {
        setPanel()
        composeRule.onNode(isHeading() and hasText("Regional")).assertIsDisplayed()
        composeRule.onNode(hasTestTag(PHONE_CHANNEL_ROW_TAG) and hasText("Regional"))
            .assertDoesNotExist()
    }

    @Test
    fun portraitPanelStartsBelowTheVideo() {
        setPanel(layout = PhoneChannelLayout.Portrait, window = DpSize(400.dp, 840.dp))
        val panel = composeRule.onNodeWithTag(PHONE_CHANNEL_PANEL_TAG).getBoundsInRoot()
        val videoBottom = 400.dp / DEFAULT_VIDEO_ASPECT
        assertTrue("panel top ${panel.top} below $videoBottom", panel.top >= videoBottom)
        composeRule.onNodeWithTag(PHONE_CHANNEL_DETAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun landscapePanelIsDockedLeftWithoutDetail() {
        setPanel(layout = PhoneChannelLayout.Landscape, window = DpSize(840.dp, 400.dp))
        assertPanelWidth(340.dp)
        composeRule.onNodeWithTag(PHONE_CHANNEL_DETAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun tabletShowsThePlayingChannelBesideTheList() {
        setPanel(layout = PhoneChannelLayout.Tablet, window = DpSize(1280.dp, 800.dp))
        assertPanelWidth(400.dp)
        composeRule.onNodeWithTag(PHONE_CHANNEL_DETAIL_TAG).assertIsDisplayed()
            .assert(hasAnyDescendant(hasText(zdf.serviceName)))
            .assert(hasAnyDescendant(hasText(zdf.now!!.description)))
            .assert(hasAnyDescendant(hasText(zdf.now!!.title)))
    }

    @Test
    fun tabletDetailFollowsThePlayingChannel() {
        var currentRef by mutableStateOf<String?>(zdf.serviceReference)
        setPanel(
            layout = PhoneChannelLayout.Tablet,
            window = DpSize(1280.dp, 800.dp),
            currentRef = { currentRef }
        )
        composeRule.runOnIdle { currentRef = swr.serviceReference }
        composeRule.onNodeWithTag(
            PHONE_CHANNEL_DETAIL_TAG
        ).assert(hasAnyDescendant(hasText(swr.serviceName)))
        composeRule.runOnIdle { currentRef = "1:0:1:DEAD:1:1:C00000:0:0:0:" }
        composeRule.onNodeWithTag(PHONE_CHANNEL_DETAIL_TAG).assertDoesNotExist()
    }

    private fun setPanel(
        layout: PhoneChannelLayout = PhoneChannelLayout.Portrait,
        window: DpSize = DpSize(400.dp, 840.dp),
        bouquets: List<Service> = this.bouquets,
        services: () -> List<ServiceNowNext> = { this.services },
        currentRef: () -> String? = { zdf.serviceReference },
        selectedBouquetRef: () -> String? = { bouquets.first().reference },
        onServiceClick: (ServiceNowNext) -> Unit = {},
        onSelectBouquet: (Service) -> Unit = {}
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(window)) {
                DreamDroidTheme(forceDark = true) {
                    PhoneChannelPanel(
                        services = services(),
                        currentRef = currentRef(),
                        bouquets = bouquets,
                        selectedBouquetRef = selectedBouquetRef(),
                        layout = layout,
                        onServiceClick = onServiceClick,
                        onSelectBouquet = onSelectBouquet
                    )
                }
            }
        }
    }

    /** The window override scales density, so px rounding moves widths by a fraction of a dp. */
    private fun assertPanelWidth(expected: Dp) {
        composeRule.onNodeWithTag(PHONE_CHANNEL_PANEL_TAG).getUnclippedBoundsInRoot().width
            .assertIsEqualTo(expected, "panel width", tolerance = 1.dp)
    }

    private fun row(name: String): SemanticsNodeInteraction =
        composeRule.onNode(hasTestTag(PHONE_CHANNEL_ROW_TAG) and hasText(name))

    private fun channel(name: String, reference: String, nowTitle: String) = ServiceNowNext(
        serviceReference = reference,
        serviceName = name,
        now = Event(title = nowTitle, description = "$nowTitle description"),
        next = Event(title = "$name next", startTimeReadable = "20:15")
    )
}
