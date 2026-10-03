package net.reichholf.dreamdroid.data

import com.google.gson.GsonBuilder
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.StreamMode
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.GenericSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun settingJson(type: String, value: String): String {
        val data = BackupData()
        data.addGenericSetting(GenericSetting("probe", value, type))
        return GsonBuilder().create().toJson(data)
    }
}
