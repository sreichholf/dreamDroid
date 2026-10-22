package net.reichholf.dreamdroid.data

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import net.reichholf.dreamdroid.DreamDroid

/** A live service that played in the integrated player. */
data class LastLiveStream(val reference: String, val serviceName: String, val bouquetRef: String?)

/**
 * The live service that last started playing, for "start the last channel" on launch and the
 * TV hub's "Continue watching".
 *
 * There is one slot. Each live stream replaces it and tags it with the active profile's id,
 * so [lastStream] has it only while that profile is active; switching to another profile and
 * playing there replaces the first profile's channel. The keys stay out of backups (see
 * [BackupRepository]).
 */
@Singleton
class LastLiveStreamRepository @Inject constructor(
    private val preferences: SharedPreferences,
    private val profiles: ProfileRepository
) {
    /** The active profile's last stream, or null; again after each [save] and profile switch. */
    val lastStream: Flow<LastLiveStream?> =
        combine(savedSlot(), profiles.current) { slot, profile ->
            slot?.takeIf { profile != null && it.profileId == profile.id }?.stream
        }.distinctUntilChanged()

    fun save(stream: LastLiveStream) {
        val profileId = profiles.current.value?.id ?: NO_PROFILE
        if (stream.reference.isBlank() || profileId == NO_PROFILE) {
            return
        }
        preferences.edit {
            putString(DreamDroid.PREFS_KEY_LAST_STREAM_REFERENCE, stream.reference)
            putString(DreamDroid.PREFS_KEY_LAST_STREAM_TITLE, stream.serviceName)
            putString(DreamDroid.PREFS_KEY_LAST_STREAM_BOUQUET, stream.bouquetRef)
            putInt(DreamDroid.PREFS_KEY_LAST_STREAM_PROFILE, profileId)
        }
    }

    private fun savedSlot(): Flow<Slot?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readSlot())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        send(readSlot())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private fun readSlot(): Slot? {
        val profileId = preferences.getInt(DreamDroid.PREFS_KEY_LAST_STREAM_PROFILE, NO_PROFILE)
        val reference = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_REFERENCE, null)
            ?.takeIf { it.isNotBlank() }
        if (profileId == NO_PROFILE || reference == null) {
            return null
        }
        return Slot(
            profileId = profileId,
            stream = LastLiveStream(
                reference = reference,
                serviceName = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_TITLE, null)
                    .orEmpty(),
                bouquetRef = preferences.getString(DreamDroid.PREFS_KEY_LAST_STREAM_BOUQUET, null)
                    ?.takeIf { it.isNotBlank() }
            )
        )
    }

    /** What the preferences hold, and the profile that played it. */
    private data class Slot(val profileId: Int, val stream: LastLiveStream)

    private companion object {
        const val NO_PROFILE = -1
    }
}
