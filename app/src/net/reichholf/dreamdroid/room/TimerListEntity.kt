package net.reichholf.dreamdroid.room

import androidx.room3.Entity
import net.reichholf.dreamdroid.enigma.Timer

/**
 * One ordered `/web/timerlist` row for a profile. Full [Timer] snapshot so the
 * hub list and MultiEPG clocks can share the same store.
 */
@Entity(
    tableName = "timer_list",
    primaryKeys = ["profileId", "position"]
)
data class TimerListEntity(
    val profileId: Int,
    val position: Int,
    val reference: String,
    val serviceName: String,
    val eit: String,
    val name: String,
    val description: String,
    val descriptionExtended: String,
    val disabled: String,
    val begin: String,
    val end: String,
    val duration: String,
    val beginReadable: String,
    val endReadable: String,
    val durationReadable: String,
    val startPrepare: String,
    val justPlay: String,
    val afterEvent: String,
    val location: String,
    val tags: String,
    val logEntries: String,
    val fileName: String,
    val backOff: String,
    val nextActivation: String,
    val firstTryPrepare: String,
    val state: String,
    val repeated: String,
    val dontSave: String,
    val canceled: String,
    val toggleDisabled: String
)

fun Timer.toListEntity(profileId: Int, position: Int): TimerListEntity = TimerListEntity(
    profileId = profileId,
    position = position,
    reference = reference,
    serviceName = serviceName,
    eit = eit,
    name = name,
    description = description,
    descriptionExtended = descriptionExtended,
    disabled = disabled,
    begin = begin,
    end = end,
    duration = duration,
    beginReadable = beginReadable,
    endReadable = endReadable,
    durationReadable = durationReadable,
    startPrepare = startPrepare,
    justPlay = justPlay,
    afterEvent = afterEvent,
    location = location,
    tags = tags,
    logEntries = logEntries,
    fileName = fileName,
    backOff = backOff,
    nextActivation = nextActivation,
    firstTryPrepare = firstTryPrepare,
    state = state,
    repeated = repeated,
    dontSave = dontSave,
    canceled = canceled,
    toggleDisabled = toggleDisabled
)

fun TimerListEntity.toTimer(): Timer = Timer(
    reference = reference,
    serviceName = serviceName,
    eit = eit,
    name = name,
    description = description,
    descriptionExtended = descriptionExtended,
    disabled = disabled,
    begin = begin,
    end = end,
    duration = duration,
    beginReadable = beginReadable,
    endReadable = endReadable,
    durationReadable = durationReadable,
    startPrepare = startPrepare,
    justPlay = justPlay,
    afterEvent = afterEvent,
    location = location,
    tags = tags,
    logEntries = logEntries,
    fileName = fileName,
    backOff = backOff,
    nextActivation = nextActivation,
    firstTryPrepare = firstTryPrepare,
    state = state,
    repeated = repeated,
    dontSave = dontSave,
    canceled = canceled,
    toggleDisabled = toggleDisabled
)
