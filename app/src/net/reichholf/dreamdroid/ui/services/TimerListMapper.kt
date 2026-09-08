package net.reichholf.dreamdroid.ui.services

import android.content.Context
import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Timer

fun timerListItemsFrom(context: Context, maps: List<ExtendedHashMap>): List<TimerListItem> {
    val states = context.resources.getTextArray(R.array.timer_state)
    val actions = context.resources.getTextArray(R.array.timer_action)
    val colors = context.resources.getIntArray(R.array.timer_state_color)
    return maps.mapIndexed { index, map ->
        var actionId = 0
        try {
            actionId = Integer.parseInt(map.getString(Timer.KEY_JUST_PLAY) ?: "0")
        } catch (e: Exception) {
            Log.e(DreamDroid.LOG_TAG, e.toString())
        }
        var stateId = 0
        try {
            stateId = Integer.parseInt(map.getString(Timer.KEY_STATE) ?: "0")
            stateId += Integer.parseInt(map.getString(Timer.KEY_DISABLED) ?: "0")
        } catch (e: Exception) {
            Log.e(DreamDroid.LOG_TAG, e.toString())
        }
        val action = if (actionId in actions.indices) actions[actionId].toString() else ""
        val state = if (stateId in states.indices) states[stateId].toString() else ""
        val color = if (stateId in colors.indices) colors[stateId] else 0
        TimerListItem(
            index = index,
            name = map.getString(Timer.KEY_NAME).orEmpty(),
            serviceName = map.getString(Timer.KEY_SERVICE_NAME).orEmpty(),
            begin = map.getString(Timer.KEY_BEGIN_READEABLE).orEmpty(),
            end = map.getString(Timer.KEY_END_READABLE).orEmpty(),
            action = action,
            state = state,
            stateColor = color,
        )
    }
}
