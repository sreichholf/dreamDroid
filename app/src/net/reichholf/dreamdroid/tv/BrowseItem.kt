package net.reichholf.dreamdroid.tv

/**
 * Settings kinds still used by the Compose TV hub. Typed service/movie payloads
 * from Phase 3.1a were unused after the hub rewrite.
 */
object BrowseItem {
    enum class Kind {
        Reload,
        Preferences,
        Profile,
    }
}
