package net.reichholf.dreamdroid.helpers.backup

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupDataGsonTest {
    @Test
    fun readsLegacyHungarianFieldNames() {
        val json =
            """{"mSettings":[{"mKey":"k","mValue":"v","mType":"String"}],"mProfiles":[]}"""
        val data = GsonBuilder().create().fromJson(json, BackupData::class.java)
        val setting = data.settings!!.single()
        assertEquals("k", setting.key)
        assertEquals("v", setting.value)
        assertEquals("String", setting.type)
    }

    @Test
    fun writesLegacyHungarianFieldNames() {
        val data = BackupData()
        data.addGenericSetting(GenericSetting("k", "v", "String"))
        val json = GsonBuilder().create().toJson(data)
        assertTrue(json.contains("\"mSettings\""))
        assertTrue(json.contains("\"mKey\""))
        assertTrue(json.contains("\"mValue\""))
        assertTrue(json.contains("\"mType\""))
    }
}
