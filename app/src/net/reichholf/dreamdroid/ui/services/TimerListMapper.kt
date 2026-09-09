package net.reichholf.dreamdroid.ui.services

import android.content.Context
import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerKeys

/**
 * Timer hub list holds typed [Timer] rows. Edit / delete / toggle still take
 * [ExtendedHashMap]; convert only at that fragment boundary.
 */
object TimerListMapper {
    @JvmStatic
    fun toExtendedHashMap(timer: Timer): ExtendedHashMap {
        val map = ExtendedHashMap()
        map.put(TimerKeys.KEY_REFERENCE, timer.reference)
        map.put(TimerKeys.KEY_SERVICE_NAME, timer.serviceName)
        map.put(TimerKeys.KEY_EIT, timer.eit)
        map.put(TimerKeys.KEY_NAME, timer.name)
        map.put(TimerKeys.KEY_DESCRIPTION, timer.description)
        map.put(TimerKeys.KEY_DESCRIPTION_EXTENDED, timer.descriptionExtended)
        map.put(TimerKeys.KEY_DISABLED, timer.disabled)
        map.put(TimerKeys.KEY_BEGIN, timer.begin)
        map.put(TimerKeys.KEY_BEGIN_READEABLE, timer.beginReadable)
        map.put(TimerKeys.KEY_END, timer.end)
        map.put(TimerKeys.KEY_END_READABLE, timer.endReadable)
        map.put(TimerKeys.KEY_DURATION, timer.duration)
        map.put(TimerKeys.KEY_DURATION_READABLE, timer.durationReadable)
        map.put(TimerKeys.KEY_START_PREPARE, timer.startPrepare)
        map.put(TimerKeys.KEY_JUST_PLAY, timer.justPlay)
        map.put(TimerKeys.KEY_AFTER_EVENT, timer.afterEvent)
        map.put(TimerKeys.KEY_LOCATION, timer.location)
        map.put(TimerKeys.KEY_TAGS, timer.tags)
        map.put(TimerKeys.KEY_LOG_ENTRIES, timer.logEntries)
        map.put(TimerKeys.KEY_FILE_NAME, timer.fileName)
        map.put(TimerKeys.KEY_BACK_OFF, timer.backOff)
        map.put(TimerKeys.KEY_NEXT_ACTIVATION, timer.nextActivation)
        map.put(TimerKeys.KEY_FIRST_TRY_PREPARE, timer.firstTryPrepare)
        map.put(TimerKeys.KEY_STATE, timer.state)
        map.put(TimerKeys.KEY_REPEATED, timer.repeated)
        map.put(TimerKeys.KEY_DONT_SAVE, timer.dontSave)
        map.put(TimerKeys.KEY_CANCELED, timer.canceled)
        map.put(TimerKeys.KEY_TOGGLE_DISABLED, timer.toggleDisabled)
        return map
    }
}

fun timerListItemsFrom(context: Context, timers: List<Timer>): List<TimerListItem> {
    val states = context.resources.getTextArray(R.array.timer_state)
    val actions = context.resources.getTextArray(R.array.timer_action)
    val colors = context.resources.getIntArray(R.array.timer_state_color)
    return timers.mapIndexed { index, timer ->
        var actionId = 0
        try {
            actionId = Integer.parseInt(timer.justPlay.ifEmpty { "0" })
        } catch (e: Exception) {
            Log.e(DreamDroid.LOG_TAG, e.toString())
        }
        var stateId = 0
        try {
            stateId = Integer.parseInt(timer.state.ifEmpty { "0" })
            stateId += Integer.parseInt(timer.disabled.ifEmpty { "0" })
        } catch (e: Exception) {
            Log.e(DreamDroid.LOG_TAG, e.toString())
        }
        val action = if (actionId in actions.indices) actions[actionId].toString() else ""
        val state = if (stateId in states.indices) states[stateId].toString() else ""
        val color = if (stateId in colors.indices) colors[stateId] else 0
        TimerListItem(
            index = index,
            name = timer.name,
            serviceName = timer.serviceName,
            begin = timer.beginReadable,
            end = timer.endReadable,
            action = action,
            state = state,
            stateColor = color,
        )
    }
}
