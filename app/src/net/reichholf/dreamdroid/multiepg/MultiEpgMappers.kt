package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.withReadableTimes
import net.reichholf.dreamdroid.room.EpgEventEntity

internal fun Event.toEpgEventEntity(
    profileId: Int,
    bouquetRef: String,
    bouquetPos: Int,
): EpgEventEntity? {
    val id = eventId.trim()
    val ref = serviceReference.trim()
    if (id.isEmpty() || ref.isEmpty()) {
        return null
    }
    val startSec = start.toLongOrNull() ?: return null
    val durationSec = duration.toLongOrNull() ?: 0L
    return EpgEventEntity(
        profileId = profileId,
        bouquetRef = bouquetRef,
        serviceRef = ref,
        eventId = id,
        start = startSec,
        duration = durationSec,
        title = title,
        description = description,
        descriptionExtended = descriptionExtended,
        serviceName = serviceName,
        currentTime = currentTime.toLongOrNull() ?: 0L,
        bouquetPos = bouquetPos,
    )
}

internal fun EpgEventEntity.toEvent(): Event {
    return Event(
        eventId = eventId,
        title = title,
        start = start.toString(),
        duration = duration.toString(),
        currentTime = if (currentTime == 0L) "" else currentTime.toString(),
        description = description,
        descriptionExtended = descriptionExtended,
        serviceReference = serviceRef,
        serviceName = serviceName,
    ).withReadableTimes()
}

/** Assign [EpgEventEntity.bouquetPos] from first-seen service order in [events]. */
internal fun List<Event>.toEpgEventEntities(
    profileId: Int,
    bouquetRef: String,
): List<EpgEventEntity> {
    val posByRef = LinkedHashMap<String, Int>()
    val out = ArrayList<EpgEventEntity>(size)
    for (event in this) {
        val ref = event.serviceReference.trim()
        if (ref.isEmpty()) {
            continue
        }
        val pos = posByRef.getOrPut(ref) { posByRef.size }
        val entity = event.toEpgEventEntity(profileId, bouquetRef, pos) ?: continue
        out.add(entity)
    }
    return out
}
