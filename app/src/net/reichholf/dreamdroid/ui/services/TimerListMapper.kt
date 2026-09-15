package net.reichholf.dreamdroid.ui.services

import android.content.Context
import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer

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
            stateColor = color
        )
    }
}
