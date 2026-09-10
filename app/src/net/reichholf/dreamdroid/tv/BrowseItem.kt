package net.reichholf.dreamdroid.tv

import net.reichholf.dreamdroid.enigma.ServiceNowNext
import java.io.Serializable
import net.reichholf.dreamdroid.enigma.Movie as EnigmaMovie

/**
 * Phase 3.1a: typed Leanback browse payload.
 * Hash maps are only built at the stream-Intent edge.
 */
sealed class BrowseItem : Serializable {
    data class Service(val row: ServiceNowNext) : BrowseItem()
    data class Movie(val movie: EnigmaMovie) : BrowseItem()
    data class Settings(
        val kind: Kind,
        val title: String,
        val iconRes: Int,
    ) : BrowseItem()

    enum class Kind {
        Reload,
        Preferences,
        Profile,
    }
}
