package net.reichholf.dreamdroid.data

import android.content.SharedPreferences

/**
 * The single-receiver profile dreamDroid 1.x kept in the default preferences. Room is the
 * profile source in 2.0, so [consume] removes the keys once the legacy import is settled.
 */
internal object LegacyProfileSettings {
    /**
     * Keys written by the 1.x TV profile editor or read by
     * [RoomProfileStore.legacyProfile]. `currentProfile` is not one of them: 2.0 uses it
     * for the active profile id.
     */
    private val KEYS = setOf(
        "profile",
        "host",
        "port",
        "ssl",
        "trust_all_certs",
        "login",
        "user",
        "pass",
        "streamlogin",
        "stream_port",
        "file_login",
        "file_port",
        "file_ssl",
        "simpleremote",
        "default_ref",
        "default_ref_name",
        "default_ref_2",
        "default_ref_2_name",
        "ssid",
        "defaultProfileOnNoWifi",
        "encoder_stream",
        "encoder_path",
        "encoder_port",
        "encoder_login",
        "encoder_user",
        "encoder_pass",
        "encoder_video_bitrate",
        "encoder_audio_bitrate"
    )

    /** Removes the 1.x profile settings in one edit. */
    fun consume(preferences: SharedPreferences) {
        val editor = preferences.edit()
        KEYS.forEach(editor::remove)
        editor.apply()
    }
}
