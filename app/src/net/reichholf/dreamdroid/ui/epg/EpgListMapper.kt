package net.reichholf.dreamdroid.ui.epg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys

/**
 * Service EPG list holds typed [Event] rows. Detail sheet / timer create still take
 * [ExtendedHashMap]; convert only at that fragment boundary.
 */
object EpgListMapper {
    @JvmStatic
    fun toExtendedHashMap(event: Event): ExtendedHashMap {
        val map = ExtendedHashMap()
        map.put(EventKeys.KEY_EVENT_ID, event.eventId)
        map.put(EventKeys.KEY_EVENT_TITLE, event.title)
        map.put(EventKeys.KEY_EVENT_START, event.start)
        map.put(EventKeys.KEY_EVENT_DURATION, event.duration)
        map.put(EventKeys.KEY_CURRENT_TIME, event.currentTime)
        map.put(EventKeys.KEY_EVENT_DESCRIPTION, event.description)
        map.put(EventKeys.KEY_EVENT_DESCRIPTION_EXTENDED, event.descriptionExtended)
        map.put(EventKeys.KEY_SERVICE_REFERENCE, event.serviceReference)
        map.put(EventKeys.KEY_SERVICE_NAME, event.serviceName)
        map.put(EventKeys.KEY_EVENT_START_READABLE, event.startReadable)
        map.put(EventKeys.KEY_EVENT_START_TIME_READABLE, event.startTimeReadable)
        map.put(EventKeys.KEY_EVENT_DURATION_READABLE, event.durationReadable)
        return map
    }
}
