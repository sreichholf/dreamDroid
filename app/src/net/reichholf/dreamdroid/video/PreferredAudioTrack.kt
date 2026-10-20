package net.reichholf.dreamdroid.video

import java.util.Locale

data class AudioTrackOption(val id: Int, val name: String)

/** Returns the first track matching a language query, or null to keep the player's default. */
fun preferredAudioTrackId(
    language: String,
    tracks: List<AudioTrackOption>
): Int? {
    val query = language.trim().lowercase(Locale.ROOT)
    if (query.isEmpty()) {
        return null
    }
    return tracks.firstOrNull { track ->
        val name = track.name.lowercase(Locale.ROOT)
        if (query.length <= 3) {
            name.split(Regex("[^a-z0-9]+")).any { it.startsWith(query) }
        } else {
            name.contains(query)
        }
    }?.id
}
