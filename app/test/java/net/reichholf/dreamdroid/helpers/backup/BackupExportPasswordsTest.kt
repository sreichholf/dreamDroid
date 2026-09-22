package net.reichholf.dreamdroid.helpers.backup

import net.reichholf.dreamdroid.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupExportPasswordsTest {
    @Test
    fun includePasswordsJsonContainsPassFields() {
        val data = sampleBackup("http-secret", "enc-secret")
        val json = serializeBackupJson(data, includePasswords = true)
        assertTrue(json.contains("\"mProfiles\""))
        assertTrue(json.contains("http-secret"))
        assertTrue(json.contains("enc-secret"))
        val parsed = checkNotNull(parseBackupImport(json))
        assertEquals("http-secret", parsed.profiles.single().pass)
        assertEquals("enc-secret", parsed.profiles.single().encoderPass)
        assertEquals("Living room", parsed.profiles.single().name)
        assertEquals("10.0.0.2", parsed.profiles.single().host)
    }

    @Test
    fun omitPasswordsClearsPassFieldsAndKeepsHostName() {
        val original = sampleProfile("http-secret", "enc-secret")
        val data = BackupData()
        data.addProfile(original)
        val json = serializeBackupJson(data, includePasswords = false)
        assertFalse(json.contains("http-secret"))
        assertFalse(json.contains("enc-secret"))
        val parsed = checkNotNull(parseBackupImport(json))
        val exported = parsed.profiles.single()
        assertEquals("", exported.pass)
        assertEquals("", exported.encoderPass)
        assertEquals("Living room", exported.name)
        assertEquals("10.0.0.2", exported.host)
        assertEquals("http-secret", original.pass)
        assertEquals("enc-secret", original.encoderPass)
    }

    @Test
    fun parseBackupImportAcceptsLegacyDocumentWithPasswords() {
        val json =
            """{"mSettings":[],"mProfiles":[{"name":"Living room","host":"10.0.0.2",""" +
                """"pass":"legacy-http","encoderPass":"legacy-enc"}]}"""
        val parsed = parseBackupImport(json)
        assertNotNull(parsed)
        val profile = parsed!!.profiles.single()
        assertEquals("Living room", profile.name)
        assertEquals("10.0.0.2", profile.host)
        assertEquals("legacy-http", profile.pass)
        assertEquals("legacy-enc", profile.encoderPass)
    }

    private fun sampleBackup(pass: String, encoderPass: String): BackupData {
        val data = BackupData()
        data.addProfile(sampleProfile(pass, encoderPass))
        return data
    }

    private fun sampleProfile(pass: String, encoderPass: String): Profile {
        val profile = Profile.getDefault()
        profile.name = "Living room"
        profile.host = "10.0.0.2"
        profile.pass = pass
        profile.encoderPass = encoderPass
        return profile
    }
}
