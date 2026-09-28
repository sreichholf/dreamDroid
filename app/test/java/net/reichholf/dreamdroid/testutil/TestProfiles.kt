package net.reichholf.dreamdroid.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Resources
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.RoomProfileStore
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * A JVM stand-in for the application context: default preferences live in memory, string
 * arrays are empty, everything else is the android.jar stub.
 */
class TestContext : ContextWrapper(null) {
    private val preferences = HashMap<String, MemorySharedPreferences>()

    @Suppress("DEPRECATION")
    private val resources = object : Resources(null, null, null) {
        override fun getStringArray(id: Int): Array<String> = emptyArray()
    }

    override fun getApplicationContext(): Context = this

    override fun getResources(): Resources = resources

    override fun getPackageName(): String = "net.reichholf.dreamdroid.test"

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { MemorySharedPreferences() }
}

/**
 * The app's profile stack over an in-memory [AppDatabase]. Tests leave it open: a
 * ViewModel job may still be finishing a blocking read when the test body returns.
 */
class TestProfiles(val context: TestContext = TestContext()) {
    val database: AppDatabase = AppDatabase.inMemory(context)
    val repository = ProfileRepository(RoomProfileStore(database, context))
}

class MemorySharedPreferences : SharedPreferences {
    private val values = HashMap<String, Any?>()

    override fun getAll(): Map<String, *> = HashMap(values)

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val changes = HashMap<String, Any?>()
        private val removals = HashSet<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = put(key, value)

        override fun putStringSet(key: String, values: Set<String>?) = put(key, values)

        override fun putInt(key: String, value: Int) = put(key, value)

        override fun putLong(key: String, value: Long) = put(key, value)

        override fun putFloat(key: String, value: Float) = put(key, value)

        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String): SharedPreferences.Editor = apply { removals += key }

        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) {
                values.clear()
            }
            removals.forEach { values.remove(it) }
            values.putAll(changes)
        }

        private fun put(key: String, value: Any?): SharedPreferences.Editor = apply {
            changes[key] = value
        }
    }
}
