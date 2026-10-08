package net.reichholf.dreamdroid.room

import android.content.SharedPreferences
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile

/**
 * The global `picons_online`, `use_name_as_picon_filename` and `sync_picons_path` values,
 * given to every profile that has no online picon settings of its own yet: rows migrated
 * from Room 12, pre-Room imports, and new profiles.
 */
data class PiconSeed(val online: Boolean, val onlineUseName: Boolean, val onlinePath: String) {
    fun applyTo(profile: Profile) {
        profile.piconsOnline = online
        profile.piconsOnlineUseName = onlineUseName
        profile.piconsOnlinePath = onlinePath
    }

    companion object {
        /**
         * dreamDroid 1.x read a missing `picons` and `picons_online` as "on" on TV, and the
         * TV `preferences.xml` declares neither, so `setDefaultValues` never writes them
         * there. Writes [isTelevision] for each missing key, so TV keeps the picons 1.x
         * showed and [from] seeds online picons from it.
         */
        fun writeMissingTvDefaults(preferences: SharedPreferences, isTelevision: Boolean) {
            val missing = listOf(
                DreamDroid.PREFS_KEY_PICONS_ENABLED,
                DreamDroid.PREFS_KEY_PICONS_ONLINE
            ).filterNot(preferences::contains)
            if (missing.isEmpty()) {
                return
            }
            preferences.edit().apply {
                missing.forEach { putBoolean(it, isTelevision) }
            }.apply()
        }

        fun from(preferences: SharedPreferences): PiconSeed = PiconSeed(
            online = preferences.getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, false),
            onlineUseName = preferences.getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false),
            onlinePath = preferences.getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, null)
                ?.takeIf { it.isNotBlank() }
                ?: Profile.DEFAULT_PICON_PATH
        )
    }
}
