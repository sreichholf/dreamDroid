package net.reichholf.dreamdroid.data

import android.content.SharedPreferences
import net.reichholf.dreamdroid.DreamDroid

data class LastLiveStream(
    val reference: String,
    val title: String,
    val bouquetRef: String?
)

/** Remembers the last live channel separately for each receiver profile. */
class LastLiveStreamStore(private val preferences: SharedPreferences) {
    fun save(reference: String, title: String, bouquetRef: String?) {
        if (reference.isBlank()) {
            return
        }
        preferences.edit()
            .putString(DreamDroid.PREFS_KEY_LAST_STREAM_REFERENCE, reference)
            .putString(DreamDroid.PREFS_KEY_LAST_STREAM_TITLE, title)
            .putString(DreamDroid.PREFS_KEY_LAST_STREAM_BOUQUET, bouquetRef)
            .putInt(
                DreamDroid.PREFS_KEY_LAST_STREAM_PROFILE,
                preferences.getInt(DreamDroid.CURRENT_PROFILE, -1)
            )
            .apply()
    }

    fun getForActiveProfile(): LastLiveStream? {
        if (
            preferences.getInt(DreamDroid.PREFS_KEY_LAST_STREAM_PROFILE, -1) !=
            preferences.getInt(DreamDroid.CURRENT_PROFILE, -1)
        ) {
            return null
        }
        val reference = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_REFERENCE, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return LastLiveStream(
            reference = reference,
            title = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_TITLE, null)
                .orEmpty(),
            bouquetRef = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_BOUQUET, null)
                ?.takeIf { it.isNotBlank() }
        )
    }
}
