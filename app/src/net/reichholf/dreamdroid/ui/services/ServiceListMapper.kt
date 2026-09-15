package net.reichholf.dreamdroid.ui.services

import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Service

fun serviceListItemsFromNowNext(rows: List<ServiceNowNext>): List<ServiceListItem> =
    rows.mapIndexed {
            index,
            row
        ->
        val ref = row.serviceReference
        val name = row.serviceName
        when {
            Service.isMarker(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.MARKER)

            Service.isDirectory(ref) -> ServiceListItem(index, ref, name, ServiceRowKind.DIRECTORY)

            else -> {
                val now = row.now
                var max = 0
                var cur = 0
                if (now != null && now.duration.isNotEmpty() && now.start.isNotEmpty() &&
                    now.duration != Python.NONE && now.start != Python.NONE
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
                    progress = cur.coerceAtLeast(0)
                )
            }
        }
    }
