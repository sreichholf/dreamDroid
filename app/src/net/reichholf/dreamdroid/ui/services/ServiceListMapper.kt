package net.reichholf.dreamdroid.ui.services

import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys
import net.reichholf.dreamdroid.helpers.enigma2.Service

fun serviceListItemsFrom(maps: List<ExtendedHashMap>): List<ServiceListItem> {
    return maps.mapIndexed { index, map ->
        EventKeys.supplementReadables(map)
        val ref = map.getString(Service.KEY_REFERENCE) ?: ""
        val name = map.getString(EventKeys.KEY_SERVICE_NAME) ?: ""
        when {
            Service.isMarker(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.MARKER)
            Service.isDirectory(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.DIRECTORY)
            else -> {
                val nextTitle = map.getString(EventKeys.PREFIX_NEXT + EventKeys.KEY_EVENT_TITLE).orEmpty()
                var max = 0
                var cur = 0
                val nowTime = map.getString(EventKeys.KEY_CURRENT_TIME)
                val duration = map.getString(EventKeys.KEY_EVENT_DURATION)
                val start = map.getString(EventKeys.KEY_EVENT_START)
                if (duration != null && start != null && duration != Python.NONE && start != Python.NONE) {
                    try {
                        max = (duration.toDouble() / 60).toLong().toInt()
                        cur = max - DateTime.getRemaining(duration, start, nowTime)
                    } catch (e: Exception) {
                        Log.e(DreamDroid.LOG_TAG, e.toString())
                    }
                }
                ServiceListItem(
                    index = index,
                    reference = ref,
                    name = name,
                    kind = ServiceRowKind.CHANNEL,
                    nowTitle = map.getString(EventKeys.KEY_EVENT_TITLE).orEmpty(),
                    nowStart = map.getString(EventKeys.KEY_EVENT_START_TIME_READABLE).orEmpty(),
                    nowDuration = map.getString(EventKeys.KEY_EVENT_DURATION_READABLE).orEmpty(),
                    nextTitle = nextTitle,
                    nextStart = map.getString(EventKeys.PREFIX_NEXT + EventKeys.KEY_EVENT_START_TIME_READABLE).orEmpty(),
                    nextDuration = map.getString(EventKeys.PREFIX_NEXT + EventKeys.KEY_EVENT_DURATION_READABLE).orEmpty(),
                    progressMax = max,
                    progress = cur.coerceAtLeast(0),
                )
            }
        }
    }
}

fun serviceListItemsFromNowNext(rows: List<ServiceNowNext>): List<ServiceListItem> {
    return rows.mapIndexed { index, row ->
        val ref = row.serviceReference
        val name = row.serviceName
        when {
            Service.isMarker(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.MARKER)
            Service.isDirectory(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.DIRECTORY)
            else -> {
                val now = row.now
                var max = 0
                var cur = 0
                if (now != null && now.duration.isNotEmpty() && now.start.isNotEmpty()
                    && now.duration != Python.NONE && now.start != Python.NONE
                ) {
                    try {
                        max = (now.duration.toDouble() / 60).toLong().toInt()
                        cur = max - DateTime.getRemaining(now.duration, now.start, now.currentTime)
                    } catch (e: Exception) {
                        Log.e(DreamDroid.LOG_TAG, e.toString())
                    }
                }
                ServiceListItem(
                    index = index,
                    reference = ref,
                    name = name,
                    kind = ServiceRowKind.CHANNEL,
                    nowTitle = now?.title.orEmpty(),
                    nowStart = now?.startTimeReadable.orEmpty(),
                    nowDuration = now?.durationReadable.orEmpty(),
                    nextTitle = row.next?.title.orEmpty(),
                    nextStart = row.next?.startTimeReadable.orEmpty(),
                    nextDuration = row.next?.durationReadable.orEmpty(),
                    progressMax = max,
                    progress = cur.coerceAtLeast(0),
                )
            }
        }
    }
}

/**
 * Combined now+next hash for stream Intent / legacy edges that still expect PREFIX_NEXT keys.
 */
fun serviceNowNextToExtendedHashMap(row: ServiceNowNext): ExtendedHashMap {
    val map = ExtendedHashMap()
    map.put(EventKeys.KEY_SERVICE_REFERENCE, row.serviceReference)
    map.put(EventKeys.KEY_SERVICE_NAME, row.serviceName)
    putEventFields(map, "", row.now)
    putEventFields(map, EventKeys.PREFIX_NEXT, row.next)
    return map
}

/** Inverse of [serviceNowNextToExtendedHashMap] for Intent / legacy hash edges. */
fun serviceNowNextFromExtendedHashMap(map: ExtendedHashMap): ServiceNowNext {
    val serviceReference = map.getString(EventKeys.KEY_SERVICE_REFERENCE).orEmpty()
    val serviceName = map.getString(EventKeys.KEY_SERVICE_NAME).orEmpty()
    return ServiceNowNext(
        serviceReference = serviceReference,
        serviceName = serviceName,
        now = eventFromPrefixedMap(map, ""),
        next = eventFromPrefixedMap(map, EventKeys.PREFIX_NEXT),
    )
}

private fun eventFromPrefixedMap(map: ExtendedHashMap, prefix: String): Event? {
    val eventId = map.getString(prefix + EventKeys.KEY_EVENT_ID).orEmpty()
    val title = map.getString(prefix + EventKeys.KEY_EVENT_TITLE).orEmpty()
    val start = map.getString(prefix + EventKeys.KEY_EVENT_START).orEmpty()
    val duration = map.getString(prefix + EventKeys.KEY_EVENT_DURATION).orEmpty()
    val currentTime = map.getString(prefix + EventKeys.KEY_CURRENT_TIME).orEmpty()
    val description = map.getString(prefix + EventKeys.KEY_EVENT_DESCRIPTION).orEmpty()
    val descriptionExtended = map.getString(prefix + EventKeys.KEY_EVENT_DESCRIPTION_EXTENDED).orEmpty()
    val startReadable = map.getString(prefix + EventKeys.KEY_EVENT_START_READABLE).orEmpty()
    val startTimeReadable = map.getString(prefix + EventKeys.KEY_EVENT_START_TIME_READABLE).orEmpty()
    val durationReadable = map.getString(prefix + EventKeys.KEY_EVENT_DURATION_READABLE).orEmpty()
    if (eventId.isEmpty() && title.isEmpty() && start.isEmpty() && duration.isEmpty()
        && currentTime.isEmpty() && description.isEmpty() && descriptionExtended.isEmpty()
        && startReadable.isEmpty() && startTimeReadable.isEmpty() && durationReadable.isEmpty()
    ) {
        return null
    }
    val serviceReference = if (prefix.isEmpty()) {
        map.getString(EventKeys.KEY_SERVICE_REFERENCE).orEmpty()
    } else {
        ""
    }
    val serviceName = if (prefix.isEmpty()) {
        map.getString(EventKeys.KEY_SERVICE_NAME).orEmpty()
    } else {
        ""
    }
    return Event(
        eventId = eventId,
        title = title,
        start = start,
        duration = duration,
        currentTime = currentTime,
        description = description,
        descriptionExtended = descriptionExtended,
        serviceReference = serviceReference,
        serviceName = serviceName,
        startReadable = startReadable,
        startTimeReadable = startTimeReadable,
        durationReadable = durationReadable,
    )
}

private fun putEventFields(map: ExtendedHashMap, prefix: String, event: Event?) {
    if (event == null) {
        return
    }
    map.put(prefix + EventKeys.KEY_EVENT_ID, event.eventId)
    map.put(prefix + EventKeys.KEY_EVENT_TITLE, event.title)
    map.put(prefix + EventKeys.KEY_EVENT_START, event.start)
    map.put(prefix + EventKeys.KEY_EVENT_DURATION, event.duration)
    map.put(prefix + EventKeys.KEY_CURRENT_TIME, event.currentTime)
    map.put(prefix + EventKeys.KEY_EVENT_DESCRIPTION, event.description)
    map.put(prefix + EventKeys.KEY_EVENT_DESCRIPTION_EXTENDED, event.descriptionExtended)
    map.put(prefix + EventKeys.KEY_EVENT_START_READABLE, event.startReadable)
    map.put(prefix + EventKeys.KEY_EVENT_START_TIME_READABLE, event.startTimeReadable)
    map.put(prefix + EventKeys.KEY_EVENT_DURATION_READABLE, event.durationReadable)
    if (prefix.isEmpty()) {
        if (event.serviceReference.isNotEmpty()) {
            map.put(EventKeys.KEY_SERVICE_REFERENCE, event.serviceReference)
        }
        if (event.serviceName.isNotEmpty()) {
            map.put(EventKeys.KEY_SERVICE_NAME, event.serviceName)
        }
    }
}
