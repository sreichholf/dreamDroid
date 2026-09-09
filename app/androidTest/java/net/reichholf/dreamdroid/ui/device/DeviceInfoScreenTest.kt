package net.reichholf.dreamdroid.ui.device

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.DeviceFrontend
import net.reichholf.dreamdroid.enigma.DeviceHdd
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.DeviceNic
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceInfoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsScalarsAndSections() {
        val state = DeviceInfoUiState()
        state.apply(
            DeviceInfo(
                guiVersion = "2016-07-28",
                imageVersion = "9.0.3.",
                interfaceVersion = "1.7.4",
                frontProcessorVersion = "0",
                deviceName = "Solo4K",
                frontends = listOf(DeviceFrontend("Tuner A", "DVB-S2")),
                nics = listOf(DeviceNic("eth0", "00:11", "True", "192.168.0.8", "192.168.0.1", "255.255.255.0")),
                hdds = listOf(DeviceHdd("ATA Disk", "1.82 TB", "1405 GB")),
            ),
        ) { capacity, free -> "$capacity ($free free)" }

        composeRule.setContent {
            DreamDroidTheme {
                DeviceInfoScreen(state = state)
            }
        }

        composeRule.onNodeWithText("GUI version").assertIsDisplayed()
        composeRule.onNodeWithText("Solo4K").assertIsDisplayed()
        composeRule.onNodeWithText("1.7.4").assertIsDisplayed()
        composeRule.onNodeWithText("Frontends").assertIsDisplayed()
        composeRule.onNodeWithText("Tuner A").assertIsDisplayed()
        composeRule.onNodeWithText("Network Interfaces").assertIsDisplayed()
        composeRule.onNodeWithText("eth0").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.0.8").assertIsDisplayed()
        composeRule.onNodeWithText("Hard disks").assertIsDisplayed()
        composeRule.onNodeWithText("ATA Disk").assertIsDisplayed()
        composeRule.onNodeWithText("1.82 TB (1405 GB free)").assertIsDisplayed()
    }

    @Test
    fun loadingPlaceholdersBeforeReady() {
        composeRule.setContent {
            DreamDroidTheme {
                DeviceInfoScreen(state = DeviceInfoUiState())
            }
        }
        composeRule.onNodeWithText("Loading…").assertIsDisplayed()
    }
}
