package net.reichholf.dreamdroid.ui.epg

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The channel tile in the EPG detail, its action label, and the plain-text fallback. */
class EpgChannelRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun channelTileOpensTheChannelEpg() {
        var opened = 0
        show(onChannelClick = { opened++ })

        composeRule.onNodeWithTag(EPG_CHANNEL_ROW_TAG).assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun channelTileNamesTheAction() {
        show(onChannelClick = {})

        val node = composeRule.onNodeWithTag(EPG_CHANNEL_ROW_TAG)
            .assertHasClickAction()
            .fetchSemanticsNode()

        assertEquals(
            context.getString(R.string.channel_epg_open),
            node.config[SemanticsActions.OnClick].label
        )
    }

    @Test
    fun withoutCallbackTheChannelStaysPlainText() {
        show(onChannelClick = null)

        composeRule.onNodeWithTag(EPG_CHANNEL_ROW_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
    }

    @Test
    fun blankServiceReferenceHidesTheTile() {
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventPane(
                    state = EpgEventDetailUiState(),
                    event = Event(
                        title = "Tatort",
                        serviceName = "Das Erste HD",
                        serviceReference = ""
                    ),
                    actions = EpgEventActions({}, {}, {}, {}, {}, onChannelEpg = {}),
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag(EPG_CHANNEL_ROW_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
    }

    private fun show(onChannelClick: (() -> Unit)?) {
        composeRule.setContent {
            DreamDroidTheme {
                EpgDetailScreen(
                    content = EpgDetailContent(
                        title = "Tatort",
                        serviceName = "Das Erste HD",
                        description = "Kommissarin Franka ermittelt in einem neuen Fall.",
                        descriptionExtended = "",
                        dateLine = "20:15 (90 Min.)",
                        isNext = false
                    ),
                    onSetTimer = {},
                    onEditTimer = {},
                    onImdb = {},
                    onSimilar = {},
                    onChannelClick = onChannelClick
                )
            }
        }
    }
}
