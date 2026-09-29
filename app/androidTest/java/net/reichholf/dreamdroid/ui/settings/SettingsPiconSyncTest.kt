package net.reichholf.dreamdroid.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.testing.HiltComposeTestActivity
import net.reichholf.dreamdroid.testutil.FakePiconSync
import net.reichholf.dreamdroid.testutil.unusedNavHandle
import net.reichholf.dreamdroid.ui.nav.LocalShellSnackbarHostState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Sync Picons on the real settings destination and `SettingsViewModel`, with
 * [FakePiconSync] bound in place of WorkManager: the shell snackbar says the sync
 * started, and a second tap says one is still running.
 */
@HiltAndroidTest
class SettingsPiconSyncTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltComposeTestActivity>()

    @Inject
    lateinit var piconSync: FakePiconSync

    @Inject
    lateinit var settings: SettingsRepository

    private var piconsBefore = false

    @Before
    fun setUp() {
        hiltRule.inject()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Otherwise the tap opens the system permission dialog over the screen.
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                composeRule.activity.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val (before, _) = settings.update { it.copy(picons = true) }
        piconsBefore = before.picons
    }

    @After
    fun restorePicons() {
        settings.update { it.copy(picons = piconsBefore) }
    }

    @Test
    fun secondTapSaysASyncIsStillRunning() {
        composeRule.setContent {
            DreamDroidTheme {
                val hostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalShellSnackbarHostState provides hostState) {
                    SettingsDestination(handle = unusedNavHandle())
                    SnackbarHost(hostState)
                }
            }
        }

        composeRule.onNodeWithText("Sync Picons").performScrollTo().performClick()
        awaitText(composeRule.activity.getString(R.string.picon_sync_started))

        composeRule.onNodeWithText("Sync Picons").performScrollTo().performClick()
        awaitText(composeRule.activity.getString(R.string.picon_sync_running))

        assertEquals(2, piconSync.enqueueCalls)
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
