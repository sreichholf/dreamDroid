package net.reichholf.dreamdroid.ui.nav

/**
 * Shared Bundle/Intent extras for phone NavHost destinations and host result wiring.
 * Keep wire keys stable for Bundle/Intent compat with older callers.
 */
object NavExtras {
    /** Serializable typed payload (`"data"`): [net.reichholf.dreamdroid.enigma.Timer],
     * [net.reichholf.dreamdroid.enigma.Service], or [net.reichholf.dreamdroid.Profile]. */
    const val DATA = "data"

    /** Intent action string for create vs edit (`"action"`). */
    const val ACTION = "action"
}
