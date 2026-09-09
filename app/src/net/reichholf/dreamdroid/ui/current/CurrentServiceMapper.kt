package net.reichholf.dreamdroid.ui.current

import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.CurrentService as CurrentServiceKeys
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.ui.epg.EpgListMapper

/**
 * Current service screen holds typed [CurrentService]. Detail sheet / timer create
 * still take [ExtendedHashMap]; convert only at that fragment boundary.
 */
object CurrentServiceMapper {
    @JvmStatic
    fun eventToExtendedHashMap(event: Event?): ExtendedHashMap? {
        if (event == null) {
            return null
        }
        return EpgListMapper.toExtendedHashMap(event)
    }

    @JvmStatic
    fun serviceToExtendedHashMap(service: Service?): ExtendedHashMap {
        val map = ExtendedHashMap()
        if (service == null) {
            return map
        }
        map.put(ServiceKeys.KEY_REFERENCE, service.reference)
        map.put(ServiceKeys.KEY_NAME, service.name)
        map.put(CurrentServiceKeys.KEY_SERVICE_PROVIDER, service.provider)
        return map
    }

    @JvmStatic
    fun toExtendedHashMap(current: CurrentService?): ExtendedHashMap {
        val map = ExtendedHashMap()
        if (current == null) {
            return map
        }
        map.put(CurrentServiceKeys.KEY_SERVICE, serviceToExtendedHashMap(current.service))
        val events = ArrayList<ExtendedHashMap>()
        current.now?.let { events.add(EpgListMapper.toExtendedHashMap(it)) }
        current.next?.let { events.add(EpgListMapper.toExtendedHashMap(it)) }
        map.put(CurrentServiceKeys.KEY_EVENTS, events)
        return map
    }
}
