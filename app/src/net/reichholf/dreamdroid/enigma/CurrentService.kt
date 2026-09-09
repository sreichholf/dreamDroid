package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * Typed `/web/getcurrent` payload: the tuned [Service] plus now/next [Event]s.
 */
data class CurrentService(
    val service: Service = Service("", ""),
    val now: Event? = null,
    val next: Event? = null
) : Serializable {
    fun isEmpty(): Boolean {
        return service.reference.isEmpty() && service.name.isEmpty() && now == null && next == null
    }
}
