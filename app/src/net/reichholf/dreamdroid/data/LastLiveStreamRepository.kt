package net.reichholf.dreamdroid.data

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.DreamDroid

/** A live service that played in the integrated player. */
data class LastLiveStream(val reference: String, val serviceName: String, val bouquetRef: String?)

/**
 * The live service that last started playing, for "start the last channel" on launch.
 *
 * There is one slot. Each live stream replaces it and tags it with the active profile's id,
 * so [forActiveProfile] returns it only while that profile is active; switching to another
 * profile and playing there replaces the first profile's channel. The keys stay out of
 * backups (see [BackupRepository]).
 */
@Singleton
class LastLiveStreamRepository @Inject constructor(
    private val preferences: SharedPreferences,
    private val profiles: ProfileRepository
) {
    fun save(stream: LastLiveStream) {
        val profileId = activeProfileId()
        if (stream.reference.isBlank() || profileId == NO_PROFILE) {
            return
        }
        preferences.edit {
            putString(DreamDroid.PREFS_KEY_LAST_STREAM_REFERENCE, stream.reference)
            putString(DreamDroid.PREFS_KEY_LAST_STREAM_SERVICE_NAME, stream.serviceName)
            putString(DreamDroid.PREFS_KEY_LAST_STREAM_BOUQUET, stream.bouquetRef)
            putInt(DreamDroid.PREFS_KEY_LAST_STREAM_PROFILE, profileId)
        }
    }

    fun forActiveProfile(): LastLiveStream? {
        val profileId = activeProfileId()
        if (profileId == NO_PROFILE ||
            preferences.getInt(DreamDroid.PREFS_KEY_LAST_STREAM_PROFILE, NO_PROFILE) != profileId
        ) {
            return null
        }
        val reference = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_REFERENCE, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return LastLiveStream(
            reference = reference,
            serviceName = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_SERVICE_NAME, null)
                .orEmpty(),
            bouquetRef = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_BOUQUET, null)
                ?.takeIf { it.isNotBlank() }
        )
    }

    private fun activeProfileId(): Int = profiles.current.value?.id ?: NO_PROFILE

    private companion object {
        const val NO_PROFILE = -1
    }
}
