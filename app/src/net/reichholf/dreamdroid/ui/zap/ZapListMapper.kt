package net.reichholf.dreamdroid.ui.zap

import net.reichholf.dreamdroid.enigma.Service

/**
 * Zap channel grid filters. Channel rows come typed from
 * [net.reichholf.dreamdroid.enigma.EnigmaClient]. Bouquet picker Intent extras carry
 * [Service] directly.
 */
object ZapListMapper {
    fun rowsFrom(services: List<Service>): List<Service> = services.filter { service ->
        !net.reichholf.dreamdroid.helpers.enigma2.Service.isMarker(service.reference)
    }
}
