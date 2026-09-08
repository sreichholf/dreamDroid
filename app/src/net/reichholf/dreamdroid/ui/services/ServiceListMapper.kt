package net.reichholf.dreamdroid.ui.services

import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.helpers.enigma2.Service

fun serviceListItemsFrom(maps: List<ExtendedHashMap>): List<ServiceListItem> {
    return maps.mapIndexed { index, map ->
        Event.supplementReadables(map)
        val ref = map.getString(Service.KEY_REFERENCE) ?: ""
        val name = map.getString(Event.KEY_SERVICE_NAME) ?: ""
        when {
            Service.isMarker(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.MARKER)
            Service.isDirectory(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.DIRECTORY)
            else -> {
                val nextTitle = map.getString(Event.PREFIX_NEXT + Event.KEY_EVENT_TITLE).orEmpty()
                var max = 0
                var cur = 0
                val nowTime = map.getString(Event.KEY_CURRENT_TIME)
                val duration = map.getString(Event.KEY_EVENT_DURATION)
                val start = map.getString(Event.KEY_EVENT_START)
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
                    nowTitle = map.getString(Event.KEY_EVENT_TITLE).orEmpty(),
                    nowStart = map.getString(Event.KEY_EVENT_START_TIME_READABLE).orEmpty(),
                    nowDuration = map.getString(Event.KEY_EVENT_DURATION_READABLE).orEmpty(),
                    nextTitle = nextTitle,
                    nextStart = map.getString(Event.PREFIX_NEXT + Event.KEY_EVENT_START_TIME_READABLE).orEmpty(),
                    nextDuration = map.getString(Event.PREFIX_NEXT + Event.KEY_EVENT_DURATION_READABLE).orEmpty(),
                    progressMax = max,
                    progress = cur.coerceAtLeast(0),
                )
            }
        }
    }
}
