package net.reichholf.dreamdroid.helpers.backup

import com.google.gson.GsonBuilder
import net.reichholf.dreamdroid.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupPasswordPolicyTest {
    @Test
    fun legacyNullCountsAsIncludedAndReplacesPassword() {
        val json = """
            {"mProfiles":[{"name":"Box","host":"10.0.0.2","pass":"secret","encoderPass":"enc"}]}
        """.trimIndent()
        val data = GsonBuilder().create().fromJson(json, BackupData::class.java)
        assertNull(data.passwordsIncluded)
        val existing = profile(
            name = "Box",
            pass = "old",
            encoderPass = "old-enc",
            host = "10.0.0.1"
        )
        val inserted = profileToInsert(
            data.profiles.single(),
            existing,
            data.passwordsIncluded
        )
        assertEquals("secret", inserted.pass)
        assertEquals("enc", inserted.encoderPass)
        assertEquals("10.0.0.2", inserted.host)
    }

    @Test
    fun explicitTrueReplacesPassword() {
        val incoming = profile(name = "Box", pass = "new-pass", encoderPass = "new-enc")
        val existing = profile(name = "Box", pass = "old-pass", encoderPass = "old-enc")
        val inserted = profileToInsert(incoming, existing, passwordsIncluded = true)
        assertEquals("new-pass", inserted.pass)
        assertEquals("new-enc", inserted.encoderPass)
    }

    @Test
    fun explicitFalseKeepsExistingPassword() {
        val incoming = profile(
            name = "Box",
            pass = "from-file",
            encoderPass = "from-file-enc",
            host = "10.0.0.8"
        )
        val existing = profile(
            name = "Box",
            pass = "kept",
            encoderPass = "kept-enc",
            host = "10.0.0.1"
        )
        val inserted = profileToInsert(incoming, existing, passwordsIncluded = false)
        assertEquals("kept", inserted.pass)
        assertEquals("kept-enc", inserted.encoderPass)
        assertEquals("10.0.0.8", inserted.host)
        assertEquals("kept", existing.pass)
        assertEquals("kept-enc", existing.encoderPass)
    }

    @Test
    fun explicitFalseWithNoExistingProfileLeavesPasswordEmpty() {
        val incoming = profile(name = "Box", pass = "from-file", encoderPass = "from-file-enc")
        val inserted = profileToInsert(incoming, existing = null, passwordsIncluded = false)
        assertEquals("", inserted.pass)
        assertEquals("", inserted.encoderPass)
        assertEquals("Box", inserted.name)
    }

    @Test
    fun exportCopyClearsPasswordsWithoutMutatingSource() {
        val source = BackupData()
        source.addGenericSetting(GenericSetting("label", "living-room", "String"))
        val original = profile(name = "Box", pass = "secret-pass", encoderPass = "secret-enc")
        source.addProfile(original)
        val copy = backupCopyForExport(source, includePasswords = false)
        source.settings = null
        assertEquals("secret-pass", original.pass)
        assertEquals("secret-enc", original.encoderPass)
        assertEquals("", copy.profiles.single().pass)
        assertEquals("", copy.profiles.single().encoderPass)
        assertEquals(false, copy.passwordsIncluded)
        assertEquals("living-room", copy.settings!!.single().value)
        val json = GsonBuilder().create().toJson(copy)
        assertTrue(json.contains("\"passwordsIncluded\":false"))
        assertFalse(json.contains("secret-pass"))
        assertFalse(json.contains("secret-enc"))
    }

    @Test
    fun exportCopyKeepsPasswordsWhenIncluded() {
        val source = BackupData()
        source.addProfile(profile(name = "Box", pass = "secret-pass", encoderPass = "secret-enc"))
        val copy = backupCopyForExport(source, includePasswords = true)
        assertEquals("secret-pass", source.profiles.single().pass)
        assertEquals("secret-pass", copy.profiles.single().pass)
        assertEquals("secret-enc", copy.profiles.single().encoderPass)
        assertEquals(true, copy.passwordsIncluded)
        val json = GsonBuilder().create().toJson(copy)
        assertTrue(json.contains("\"passwordsIncluded\":true"))
        assertTrue(json.contains("secret-pass"))
        assertTrue(json.contains("secret-enc"))
    }

    private fun profile(
        name: String,
        pass: String,
        encoderPass: String,
        host: String = "10.0.0.1"
    ): Profile = Profile.getDefault().apply {
        this.name = name
        this.pass = pass
        this.encoderPass = encoderPass
        this.host = host
    }
}
