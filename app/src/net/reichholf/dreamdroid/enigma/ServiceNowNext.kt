package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * One hub TV/Radio row from `/web/epgnownext` (or epgnow fallback): service plus now/next [Event]s.
 */
data class ServiceNowNext(
    val serviceReference: String = "",
    val serviceName: String = "",
    val now: Event? = null,
    val next: Event? = null
) : Serializable
