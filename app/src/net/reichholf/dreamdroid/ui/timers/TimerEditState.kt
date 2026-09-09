package net.reichholf.dreamdroid.ui.timers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compose form state for timer create/edit. Persist [ExtendedHashMap] on the fragment
 * for save/pick edges; sync display fields here.
 */
class TimerEditState {
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var enabled by mutableStateOf(true)
    var zap by mutableStateOf(false)
    var beginDate by mutableStateOf("...")
    var beginTime by mutableStateOf("...")
    var endDate by mutableStateOf("...")
    var endTime by mutableStateOf("...")
    var repeatedLabel by mutableStateOf("")
    var serviceName by mutableStateOf("")
    var tagsLabel by mutableStateOf("")
    var afterEventIndex by mutableIntStateOf(Timer.Afterevents.AUTO.intValue())
    var locationIndex by mutableIntStateOf(0)
    var afterEventOptions by mutableStateOf<List<String>>(emptyList())
    var locationOptions by mutableStateOf<List<String>>(emptyList())

    fun loadFrom(
        timer: ExtendedHashMap,
        afterEvents: List<String>,
        locations: List<String>,
        repeatedLabel: String,
    ) {
        name = timer.getString(Timer.KEY_NAME).orEmpty()
        description = timer.getString(Timer.KEY_DESCRIPTION).orEmpty()
        enabled = DateTime.parseTimestamp(timer.getString(Timer.KEY_DISABLED)) == 0
        zap = DateTime.parseTimestamp(timer.getString(Timer.KEY_JUST_PLAY)) == 1
        serviceName = timer.getString(Timer.KEY_SERVICE_NAME).orEmpty()
        afterEventOptions = afterEvents
        locationOptions = locations
        afterEventIndex = DateTime.parseTimestamp(timer.getString(Timer.KEY_AFTER_EVENT))
            .coerceIn(0, (afterEvents.size - 1).coerceAtLeast(0))

        val timerLoc = timer.getString(Timer.KEY_LOCATION)
        locationIndex = 0
        if (timerLoc != null) {
            val idx = locations.indexOf(timerLoc)
            if (idx >= 0) locationIndex = idx
        }

        val begin = DateTime.parseTimestamp(timer.getString(Timer.KEY_BEGIN))
        val end = DateTime.parseTimestamp(timer.getString(Timer.KEY_END))
        setBeginEndLabels(begin, end)
        this.repeatedLabel = repeatedLabel
        tagsLabel = timer.getString(Timer.KEY_TAGS).orEmpty()
    }

    fun setBeginEndLabels(beginSeconds: Int, endSeconds: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
        val begin = Date(beginSeconds.toLong() * 1000)
        val end = Date(endSeconds.toLong() * 1000)
        beginDate = dateFormat.format(begin)
        beginTime = timeFormat.format(begin)
        endDate = dateFormat.format(end)
        endTime = timeFormat.format(end)
    }

    fun applyTo(timer: ExtendedHashMap) {
        timer.put(Timer.KEY_NAME, name)
        timer.put(Timer.KEY_DESCRIPTION, description)
        timer.put(Timer.KEY_DISABLED, if (enabled) "0" else "1")
        timer.put(Timer.KEY_JUST_PLAY, if (zap) "1" else "0")
        timer.put(Timer.KEY_AFTER_EVENT, afterEventIndex.toString())
        if (locationOptions.isNotEmpty() && locationIndex in locationOptions.indices) {
            timer.put(Timer.KEY_LOCATION, locationOptions[locationIndex])
        }
    }
}

fun ComposeView.bindTimerEditScreen(
    state: TimerEditState,
    saveLabel: String,
    onSave: () -> Unit,
    onPickBeginDate: () -> Unit,
    onPickBeginTime: () -> Unit,
    onPickEndDate: () -> Unit,
    onPickEndTime: () -> Unit,
    onPickRepeated: () -> Unit,
    onPickService: () -> Unit,
    onPickTags: () -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            TimerEditScreen(
                state = state,
                saveLabel = saveLabel,
                onSave = onSave,
                onPickBeginDate = onPickBeginDate,
                onPickBeginTime = onPickBeginTime,
                onPickEndDate = onPickEndDate,
                onPickEndTime = onPickEndTime,
                onPickRepeated = onPickRepeated,
                onPickService = onPickService,
                onPickTags = onPickTags,
            )
        }
    }
}
