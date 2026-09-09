package net.reichholf.dreamdroid.ui.zap

import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.ExtendedHashMap

/**
 * Zap keeps XML rows. Channel rows come typed from
 * [net.reichholf.dreamdroid.enigma.EnigmaClient]. The bouquet picker holds typed
 * [Service] and maps one [ExtendedHashMap] only when putting the Intent extra.
 */
object ZapListMapper {
    @JvmStatic
    fun rowsFrom(services: List<Service>): List<Service> {
        return services.filter { service ->
            !net.reichholf.dreamdroid.helpers.enigma2.Service.isMarker(service.reference)
        }
    }

    @JvmStatic
    fun toBouquetMap(service: Service?): ExtendedHashMap {
        val map = ExtendedHashMap()
        if (service == null) {
            map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE, "")
            map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME, "")
            return map
        }
        map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE, service.reference)
        map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME, service.name)
        return map
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
