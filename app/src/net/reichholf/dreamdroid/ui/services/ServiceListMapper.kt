package net.reichholf.dreamdroid.ui.services

import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Service

/**
 * The current programme's progress in minutes. [max] and [elapsed] are 0 when the now
 * event has no usable start/duration. Shared by the phone service row and the TV card.
 */
data class NowProgress(val max: Int, val elapsed: Int) {
    val isKnown: Boolean
        get() = max > 0
}

fun nowProgress(now: Event?): NowProgress {
    if (
        now == null ||
        now.duration.isEmpty() ||
        now.start.isEmpty() ||
        now.duration == Python.NONE ||
        now.start == Python.NONE
    ) {
        return NowProgress(0, 0)
    }
    return try {
        val max = (now.duration.toDouble() / 60).toLong().toInt()
        val elapsed = max - DateTime.getRemaining(now.duration, now.start, now.currentTime)
        NowProgress(max, elapsed.coerceAtLeast(0))
    } catch (e: Exception) {
        Log.e(DreamDroid.LOG_TAG, e.toString())
        NowProgress(0, 0)
    }
}

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
                val progress = nowProgress(now)
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
                    progressMax = progress.max,
                    progress = progress.elapsed
                )
            }
        }
    }
