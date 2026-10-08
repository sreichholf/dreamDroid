package net.reichholf.dreamdroid.data

import com.google.gson.GsonBuilder
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.StreamMode
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.GenericSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupImportParseTest {
    @Test
    fun rejectsMalformedJson() {
        assertNull(parseBackupImport("{"))
        assertNull(parseBackupImport(""))
        assertNull(parseBackupImport("not json"))
        assertNull(parseBackupImport("[]"))
    }

    @Test
    fun rejectsNullDocument() {
        assertNull(parseBackupImport(null))
        assertNull(parseBackupImport("null"))
    }

    @Test
    fun rejectsBadSettingInsteadOfPartialDocument() {
        val data = BackupData()
        data.addGenericSetting(GenericSetting("label", "living-room", "String"))
        data.addGenericSetting(GenericSetting("count", "nope", "Integer"))
        val profile = Profile.getDefault()
        profile.name = "Living room"
        data.addProfile(profile)
        assertNull(parseBackupImport(GsonBuilder().create().toJson(data)))
    }

    @Test
    fun rejectsBadLongFloatAndNullSettingValue() {
        assertNull(parseBackupImport(settingJson("Long", "12x")))
        assertNull(parseBackupImport(settingJson("Float", "nope")))
        assertNull(
            parseBackupImport(
                """{"mSettings":[{"mKey":"count","mValue":null,"mType":"Integer"}],"mProfiles":[]}"""
            )
        )
    }

    @Test
    fun parsesValidProfilesAndSettings() {
        val data = BackupData()
        data.addGenericSetting(GenericSetting("flag", "true", "Boolean"))
        data.addGenericSetting(GenericSetting("count", "3", "Integer"))
        data.addGenericSetting(GenericSetting("when", "42", "Long"))
        data.addGenericSetting(GenericSetting("level", "1.5", "Float"))
        data.addGenericSetting(GenericSetting("name", "box", "String"))
        val profile = Profile.getDefault()
        profile.name = "Living room"
        profile.host = "10.0.0.2"
        data.addProfile(profile)

        val parsed = parseBackupImport(GsonBuilder().create().toJson(data))
        val imported = checkNotNull(parsed)
        assertEquals("Living room", imported.profiles.single().name)
        assertEquals("10.0.0.2", imported.profiles.single().host)
        val settings = checkNotNull(imported.settings).associate { it.key to it.value }
        assertEquals("true", settings["flag"])
        assertEquals("3", settings["count"])
        assertEquals("42", settings["when"])
        assertEquals("1.5", settings["level"])
        assertEquals("box", settings["name"])
    }

    @Test
    fun legacyEncoderFlagBecomesTheStreamMode() {
        val json = """{"mProfiles":[
            {"name":"Encoder","encoderStream":true},
            {"name":"Plain","encoderStream":false},
            {"name":"Unknown","streamMode":"Satellite"}
        ]}"""
        val profiles = checkNotNull(parseBackupImport(json)).profiles
        assertEquals(StreamMode.Encoder, profiles[0].streamMode)
        assertEquals(StreamMode.Direct, profiles[1].streamMode)
        assertEquals(StreamMode.Direct, profiles[2].streamMode)
        assertEquals(Profile.DEFAULT_TRANSCODE_PORT, profiles[0].transcodePort)
    }

    @Test
    fun streamModeRoundTrips() {
        val data = BackupData()
        data.addProfile(
            Profile.getDefault().apply {
                streamMode = StreamMode.Transcoding
                transcodePort = 8003
            }
        )
        val imported = checkNotNull(parseBackupImport(GsonBuilder().create().toJson(data)))
        assertEquals(StreamMode.Transcoding, imported.profiles.single().streamMode)
        assertEquals(8003, imported.profiles.single().transcodePort)
    }

    @Test
    fun fileWithoutProfilePiconsTakesItsGlobalOnlinePiconSettings() {
        val json = """{"mSettings":[
            {"mKey":"picons_online","mValue":"true","mType":"Boolean"},
            {"mKey":"use_name_as_picon_filename","mValue":"true","mType":"Boolean"},
            {"mKey":"sync_picons_path","mValue":"/media/hdd/picon","mType":"String"}
        ],"mProfiles":[{"name":"Old"}]}"""
        val profile = checkNotNull(parseBackupImport(json)).profiles.single()
        assertTrue(profile.piconsOnline)
        assertTrue(profile.piconsOnlineUseName)
        assertEquals("/media/hdd/picon", profile.piconsOnlinePath)
    }

    @Test
    fun fileWithoutAnyPiconSettingKeepsTheProfileDefaults() {
        val profile = checkNotNull(parseBackupImport("""{"mProfiles":[{"name":"Old"}]}"""))
            .profiles.single()
        assertFalse(profile.piconsOnline)
        assertFalse(profile.piconsOnlineUseName)
        assertEquals(Profile.DEFAULT_PICON_PATH, profile.piconsOnlinePath)
    }

    @Test
    fun profilePiconSettingsWinOverTheFileGlobals() {
        val data = BackupData()
        data.addGenericSetting(GenericSetting("picons_online", "true", "Boolean"))
        data.addGenericSetting(GenericSetting("sync_picons_path", "/media/hdd/picon", "String"))
        data.addProfile(
            Profile.getDefault().apply {
                piconsOnline = false
                piconsOnlinePath = "/media/usb/picon"
            }
        )
        val profile = checkNotNull(parseBackupImport(GsonBuilder().create().toJson(data)))
            .profiles.single()
        assertFalse(profile.piconsOnline)
        assertEquals("/media/usb/picon", profile.piconsOnlinePath)
    }

    private fun settingJson(type: String, value: String): String {
        val data = BackupData()
        data.addGenericSetting(GenericSetting("probe", value, type))
        return GsonBuilder().create().toJson(data)
    }
}
