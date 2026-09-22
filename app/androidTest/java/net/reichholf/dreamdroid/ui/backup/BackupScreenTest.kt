package net.reichholf.dreamdroid.ui.backup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.BackupService
import net.reichholf.dreamdroid.helpers.backup.GenericSetting
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BackupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @After
    fun deleteF05Artifacts() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .remove("f05_import_probe")
            .commit()
        val dao = AppDatabase.profilesBlocking(ctx)
        dao.getProfiles()
            .filter { it.name?.startsWith("f05-") == true }
            .forEach { dao.deleteProfile(it) }
    }

    @Test
    fun keyLabelsAndButtonsVisible() {
        val state = BackupUiState().apply {
            replaceProfiles(
                listOf(
                    BackupProfileToggle(
                        id = 1,
                        label = "Home (192.168.1.1) (current)",
                        checked = true
                    )
                )
            )
        }
        composeRule.setContent {
            DreamDroidTheme {
                BackupScreen(
                    state = state,
                    onImport = {},
                    onExport = {}
                )
            }
        }

        composeRule.onNodeWithText("Import").assertIsDisplayed()
        composeRule.onNodeWithText("Export").assertIsDisplayed()
        composeRule.onNodeWithText("Profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Home (192.168.1.1) (current)").assertIsDisplayed()
        composeRule.onNodeWithText("Include passwords").assertIsDisplayed()
        composeRule.onNodeWithText("Include passwords").assertIsOn()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Export settings").assertIsDisplayed()
        composeRule.onNodeWithText("Home (192.168.1.1) (current)").assertIsOn()
        composeRule.onNodeWithText("Export settings").assertIsOff()
        composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG).assertCountEquals(3)
        composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG)[0]
            .assertLeftPositionInRootIsEqualTo(8.dp)
        val exportSettings = composeRule.onNode(hasText("Export settings") and isToggleable())
            .getBoundsInRoot()
        val exportHeight = exportSettings.bottom - exportSettings.top
        assertTrue(
            "Switch rows are at least 56.dp, height=$exportHeight",
            exportHeight >= 56.dp
        )
    }

    @Test
    fun exportWithPasswordsShowsConfirmBeforeCallback() {
        val state = BackupUiState()
        var exportCalls = 0
        composeRule.setContent {
            DreamDroidTheme {
                BackupScreen(
                    state = state,
                    onImport = {},
                    onExport = { exportCalls++ }
                )
            }
        }

        composeRule.onNodeWithText("Include passwords").assertIsOn()
        composeRule.onNodeWithText("Export").performClick()
        composeRule.onNodeWithText("Export passwords?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, exportCalls) }
        composeRule.onNodeWithText("OK").performClick()
        composeRule.runOnIdle { assertEquals(1, exportCalls) }
    }

    @Test
    fun exportWithoutPasswordsSkipsConfirm() {
        val state = BackupUiState().apply { includePasswords = false }
        var exportCalls = 0
        composeRule.setContent {
            DreamDroidTheme {
                BackupScreen(
                    state = state,
                    onImport = {},
                    onExport = { exportCalls++ }
                )
            }
        }

        composeRule.onNodeWithText("Include passwords").assertIsOff()
        composeRule.onNodeWithText("Export").performClick()
        composeRule.onNodeWithText("Export passwords?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, exportCalls) }
    }

    @Test
    fun failedExportDoesNotToastSuccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = backupExportUserMessage(context, exported = false)
        assertEquals(
            context.getString(net.reichholf.dreamdroid.R.string.backup_export_write_failed),
            message
        )
        assertTrue(
            backupExportUserMessage(context, exported = true) !=
                backupExportUserMessage(context, exported = false)
        )
    }

    @Test
    fun importPersistsSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().remove("f05_import_probe").commit()
        val data = BackupData()
        data.addGenericSetting(
            GenericSetting("f05_import_probe", "from-backup", "String")
        )
        BackupService(context).doImport(GsonBuilder().create().toJson(data))
        assertEquals("from-backup", prefs.getString("f05_import_probe", null))
    }

    @Test
    fun importDoesNotReplaceDifferentNamedProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = AppDatabase.profilesBlocking(context)
        val kitchen = Profile.getDefault().apply {
            name = "f05-kitchen"
            host = "10.0.0.1"
        }
        kitchen.id = dao.addProfile(kitchen).toInt()
        val incoming = Profile.getDefault().apply {
            id = kitchen.id
            name = "f05-bedroom"
            host = "9.9.9.9"
        }
        val data = BackupData()
        data.addProfile(incoming)
        BackupService(context).doImport(GsonBuilder().create().toJson(data))
        val byName = dao.getProfiles()
            .filter { it.name == "f05-kitchen" || it.name == "f05-bedroom" }
            .associate { it.name to it.host }
        assertEquals("10.0.0.1", byName["f05-kitchen"])
        assertEquals("9.9.9.9", byName["f05-bedroom"])
    }
}
