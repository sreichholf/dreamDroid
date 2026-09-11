package net.reichholf.dreamdroid.ui.nav

/**
 * Shared Bundle/Intent extras for phone NavHost destinations and host result wiring.
 * Keep wire keys stable for Bundle/Intent compat with older callers.
 */
object NavExtras {
    /** Serializable [net.reichholf.dreamdroid.helpers.ExtendedHashMap] payload (wire key `"data"`). */
    const val DATA = "data"
}
