package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.room.EpgEventEntity

internal fun Event.toEpgEventEntity(profileId: Int): EpgEventEntity? {
    val id = eventId.trim()
    val ref = serviceReference.trim()
    if (id.isEmpty() || ref.isEmpty()) {
        return null
    }
    val startSec = start.toLongOrNull() ?: return null
    val durationSec = duration.toLongOrNull() ?: 0L
    return EpgEventEntity(
        profileId = profileId,
        serviceRef = ref,
        eventId = id,
        start = startSec,
        duration = durationSec,
        title = title,
        description = description,
        descriptionExtended = descriptionExtended,
        serviceName = serviceName,
        currentTime = currentTime.toLongOrNull() ?: 0L,
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
    )
}
