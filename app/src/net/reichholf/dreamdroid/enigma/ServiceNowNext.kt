package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * One hub TV/Radio row: service identity plus optional now/next [Event]s.
 * Hub rosters come from `/web/getservices`; now/next is overlaid from
 * `/web/epgnownext` when that service has events.
 */
data class ServiceNowNext(
    val serviceReference: String = "",
    val serviceName: String = "",
    val now: Event? = null,
    val next: Event? = null
) : Serializable
