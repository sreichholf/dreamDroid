package net.reichholf.dreamdroid.ui.zap

import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.ExtendedHashMap

/**
 * Zap keeps XML rows. This mapper is the typed boundary: bouquet picker maps still
 * arrive as [ExtendedHashMap], and [net.reichholf.dreamdroid.enigma.EnigmaClient]
 * already returns [Service] from `/web/getservices`.
 */
object ZapListMapper {
    @JvmStatic
    fun rowsFrom(services: List<Service>): List<Service> {
        return services.filter { service ->
            !net.reichholf.dreamdroid.helpers.enigma2.Service.isMarker(service.reference)
        }
    }

    @JvmStatic
    fun bouquetFrom(map: ExtendedHashMap?): Service {
        if (map == null) {
            return Service("", "")
        }
        return Service(
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE) ?: "",
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME) ?: ""
        )
    }
}
