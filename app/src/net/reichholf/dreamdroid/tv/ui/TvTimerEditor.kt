package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.Collections
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.enigma.launchLocationsAndTagsLoad
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Tag
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.ui.epg.EpgDatePickerDialog
import net.reichholf.dreamdroid.ui.epg.EpgTimePickerDialog
import net.reichholf.dreamdroid.ui.timers.TimerEditScreen
import net.reichholf.dreamdroid.ui.timers.TimerEditState

internal val TV_TIMER_REPEATED_VALUES = intArrayOf(1, 2, 4, 8, 16, 32, 64)

internal fun tvTimerRepeatedValue(days: BooleanArray): Int {
    var value = 0
    for (i in days.indices) {
        if (days[i] && i < TV_TIMER_REPEATED_VALUES.size) {
            value += TV_TIMER_REPEATED_VALUES[i]
        }
    }
    return value
}

internal fun tvTimerCheckedDays(value: Int): BooleanArray {
    val days = BooleanArray(7)
    var remaining = value
    for (i in TV_TIMER_REPEATED_VALUES.indices) {
        days[i] = (remaining and 1) == 1
        remaining = remaining shr 1
    }
    return days
}

/**
 * TV / overlay timer create-edit. The working copy lives on [TvTimerEditViewModel], so
 * it survives a configuration change and the in-host service pick. Back dismisses;
 * [mutationsBlocked] save shows [TvNeedsReceiverOverlay] instead of HTTP.
 */
@Composable
fun TvTimerEditorHost(
    timer: TypedTimer,
    isCreate: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    mutationsBlocked: Boolean = false,
    viewModel: TvTimerEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val session = remember(timer, isCreate, viewModel) { viewModel.bind(timer, isCreate) }
    val currentOnSaved by rememberUpdatedState(onSaved)
    var pickingService by remember { mutableStateOf(false) }
    var showNeedsReceiver by remember { mutableStateOf(false) }
    var showRepeatingsPicker by remember { mutableStateOf(false) }
    var showTagsPicker by remember { mutableStateOf(false) }
    var pickerKind by remember { mutableStateOf<TvTimerEditPicker?>(null) }
    val is24Hour = DateFormat.is24HourFormat(context)

    BackHandler(enabled = !pickingService, onBack = onDismiss)

    DisposableEffect(session, viewModel) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                viewModel.release(session)
            }
        }
    }

    LaunchedEffect(session) {
        session.ensureLocationsAndTagsThenReload()
    }

    LaunchedEffect(session, session.saveSucceeded) {
        if (session.saveSucceeded) {
            session.saveSucceeded = false
            currentOnSaved()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (pickingService) {
            TvTimerServicePick(
                onPicked = { service ->
                    session.applyPickedService(service)
                    pickingService = false
                },
                onDismiss = { pickingService = false }
            )
        } else {
            TimerEditScreen(
                state = session.editState,
                saveLabel = stringResource(R.string.save),
                onSave = {
                    if (mutationsBlocked) {
                        showNeedsReceiver = true
                    } else {
                        session.saveTimer()
                    }
                },
                onPickBeginDate = { pickerKind = TvTimerEditPicker.BeginDate },
                onPickBeginTime = { pickerKind = TvTimerEditPicker.BeginTime },
                onPickEndDate = { pickerKind = TvTimerEditPicker.EndDate },
                onPickEndTime = { pickerKind = TvTimerEditPicker.EndTime },
                onPickRepeated = { showRepeatingsPicker = true },
                onPickService = { pickingService = true },
                onPickTags = { showTagsPicker = true },
                showSaveFab = true,
                mutating = session.progress != null,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showRepeatingsPicker) {
            val days = context.resources.getTextArray(R.array.weekdays).map { it.toString() }
            MultiChoiceAlertDialog(
                title = stringResource(R.string.choose_days),
                items = days,
                initialChecked = session.checkedDays.copyOf(),
                onDismiss = { showRepeatingsPicker = false },
                onConfirm = { indices ->
                    session.applyRepeatingsSelection(indices)
                    showRepeatingsPicker = false
                }
            )
        }
        if (showTagsPicker) {
            val tags = ProfileRepository.get().tags()
            val checked = BooleanArray(tags.size) { i ->
                session.selectedTags.contains(tags[i])
            }
            MultiChoiceAlertDialog(
                title = stringResource(R.string.choose_tags),
                items = tags,
                initialChecked = checked,
                onDismiss = { showTagsPicker = false },
                onConfirm = { indices ->
                    session.applyTagsSelection(indices)
                    showTagsPicker = false
                }
            )
        }

        when (pickerKind) {
            TvTimerEditPicker.BeginDate -> EpgDatePickerDialog(
                initialTimeSec = session.begin,
                onDismiss = { pickerKind = null },
                onConfirm = { utcDateMillis ->
                    session.applyPickedDate(isBegin = true, utcDateMillis = utcDateMillis)
                    pickerKind = null
                }
            )

            TvTimerEditPicker.EndDate -> EpgDatePickerDialog(
                initialTimeSec = session.end,
                onDismiss = { pickerKind = null },
                onConfirm = { utcDateMillis ->
                    session.applyPickedDate(isBegin = false, utcDateMillis = utcDateMillis)
                    pickerKind = null
                }
            )

            TvTimerEditPicker.BeginTime -> EpgTimePickerDialog(
                initialTimeSec = session.begin,
                is24Hour = is24Hour,
                onDismiss = { pickerKind = null },
                onConfirm = { hour, minute ->
                    session.applyPickedTime(isBegin = true, hourOfDay = hour, minute = minute)
                    pickerKind = null
                }
            )

            TvTimerEditPicker.EndTime -> EpgTimePickerDialog(
                initialTimeSec = session.end,
                is24Hour = is24Hour,
                onDismiss = { pickerKind = null },
                onConfirm = { hour, minute ->
                    session.applyPickedTime(isBegin = false, hourOfDay = hour, minute = minute)
                    pickerKind = null
                }
            )

            null -> Unit
        }

        if (showNeedsReceiver) {
            TvNeedsReceiverOverlay(onDismiss = { showNeedsReceiver = false })
        }
        IndeterminateProgressHost(session.progress)
    }
}

private enum class TvTimerEditPicker {
    BeginDate,
    BeginTime,
    EndDate,
    EndTime
}

/**
 * [context] is the application context and [scope] is the owning
 * [TvTimerEditViewModel]'s scope, so loads and saves outlive the composition.
 */
internal class TvTimerEditWorkingCopy(
    val launchTimer: TypedTimer,
    val isCreate: Boolean,
    private val context: Context,
    private val scope: CoroutineScope
) {
    var timer: TypedTimer = launchTimer
    val timerOld: TypedTimer? = if (isCreate) null else launchTimer.copy()
    val editState = TimerEditState()
    val selectedTags = ArrayList<String>()
    val checkedDays = BooleanArray(7)
    var begin: Int = 0
    var end: Int = 0
    var progress by mutableStateOf<IndeterminateProgressState?>(null)
    var saveSucceeded by mutableStateOf(false)
    private var locationsJob: Job? = null
    private var saveJob: Job? = null
    private var formHydrated = false

    fun cancelWork() {
        progress = null
        locationsJob?.cancel()
        locationsJob = null
        saveJob?.cancel()
        saveJob = null
    }

    fun applyPickedService(picked: Service) {
        timer = timer.copy(serviceName = picked.name, reference = picked.reference)
        editState.serviceName = timer.serviceName
    }

    fun applyRepeatingsSelection(indices: List<Int>) {
        checkedDays.fill(false)
        for (which in indices) {
            if (which in checkedDays.indices) {
                checkedDays[which] = true
            }
        }
        editState.repeatedLabel = setRepeated(checkedDays)
    }

    fun applyTagsSelection(indices: List<Int>) {
        val tags = ProfileRepository.get().tags()
        val next = ArrayList<String>()
        for (which in indices) {
            if (which in tags.indices) {
                next.add(tags[which])
            }
        }
        val tagsChanged = next != selectedTags
        selectedTags.clear()
        selectedTags.addAll(next)
        if (tagsChanged) {
            val joined = Tag.implodeTags(selectedTags)
            timer = timer.copy(tags = joined)
            editState.tagsLabel = joined
        }
    }

    fun applyPickedDate(isBegin: Boolean, utcDateMillis: Long) {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = utcDateMillis
        onDateSet(
            isBegin,
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun applyPickedTime(isBegin: Boolean, hourOfDay: Int, minute: Int) {
        onTimeSet(isBegin, hourOfDay, minute)
    }

    fun ensureLocationsAndTagsThenReload() {
        if (ProfileRepository.get().locations().size != 0 &&
            ProfileRepository.get().tags().size != 0
        ) {
            reload()
            return
        }
        if (locationsJob != null) {
            return
        }
        locationsJob = scope.launchLocationsAndTagsLoad(
            context,
            onProgress = { title, progressText ->
                progress = IndeterminateProgressState(title = title, message = progressText)
            },
            onReady = {
                locationsJob = null
                progress = null
                reload()
            }
        )
    }

    fun reload() {
        if (formHydrated) {
            timer = editState.applyTo(timer)
        }
        begin = DateTime.parseTimestamp(timer.begin)
        end = DateTime.parseTimestamp(timer.end)
        var repeatedValue = 0
        try {
            repeatedValue = DateTime.parseTimestamp(timer.repeated)
        } catch (_: NumberFormatException) {
        }
        val repeatedText = getRepeated(repeatedValue)
        val text = timer.tags
        selectedTags.clear()
        if (text.isNotEmpty()) {
            Collections.addAll(selectedTags, *text.split(" ").toTypedArray())
        }
        val afterEvents = context.resources.getTextArray(R.array.afterevents).map { it.toString() }
        editState.loadFrom(timer, afterEvents, ProfileRepository.get().locations(), repeatedText)
        formHydrated = true
    }

    fun saveTimer() {
        if (progress != null) {
            return
        }
        editState.saveError = ""
        progress = IndeterminateProgressState(message = context.getString(R.string.saving))
        timer = editState.applyTo(timer)
        val params = Timer.getSaveParams(timer, timerOld)
        saveJob?.cancel()
        saveJob =
            scope.launchSimpleResultLoad(TimerChangeRequestHandler(), params) { _, result, _ ->
                progress = null
                if (Python.TRUE.equals(result.state)) {
                    editState.saveError = ""
                    saveSucceeded = true
                    return@launchSimpleResultLoad
                }
                val stateText = result.stateText
                editState.saveError = when {
                    !stateText.isNullOrEmpty() -> stateText
                    else -> context.getString(R.string.get_content_error)
                }
            }
    }

    private fun getRepeated(value: Int): String {
        var remaining = value
        var text = ""
        val daysShort = context.resources.getTextArray(R.array.weekdays_short)
        for (i in TV_TIMER_REPEATED_VALUES.indices) {
            val checked = (remaining and 1) == 1
            if (checked) {
                if (text.isNotEmpty()) text = text.plus(", ")
                text = text.plus(daysShort[i].toString())
            }
            checkedDays[i] = checked
            remaining = remaining shr 1
        }
        return text.ifEmpty { context.getText(R.string.none).toString() }
    }

    private fun setRepeated(days: BooleanArray): String {
        var text = ""
        val value = tvTimerRepeatedValue(days)
        val daysShort = context.resources.getTextArray(R.array.weekdays_short)
        for (i in days.indices) {
            if (days[i]) {
                if (text.isNotEmpty()) text = text.plus(", ")
                text = text.plus(daysShort[i].toString())
            }
        }
        timer = timer.copy(repeated = value.toString())
        text = when (value) {
            31 -> context.getText(R.string.mo_to_fr).toString()
            127 -> context.getText(R.string.daily).toString()
            else -> text
        }
        return text.ifEmpty { context.getText(R.string.none).toString() }
    }

    private fun calendar(time: Int): Calendar = Calendar.getInstance().apply {
        timeInMillis = time.toLong() * 1000
    }

    private fun onDateSet(isBegin: Boolean, year: Int, month: Int, day: Int) {
        val time = if (isBegin) begin else end
        val cal = calendar(time)
        if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month &&
            cal.get(Calendar.DATE) == day
        ) {
            return
        }
        cal.set(year, month, day)
        onTimeChanged(isBegin, cal)
    }

    private fun onTimeSet(isBegin: Boolean, hourOfDay: Int, minute: Int) {
        val time = if (isBegin) begin else end
        val cal = calendar(time)
        if (cal.get(Calendar.HOUR_OF_DAY) == hourOfDay && cal.get(Calendar.MINUTE) == minute) {
            return
        }
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(Calendar.MINUTE, minute)
        onTimeChanged(isBegin, cal)
    }

    private fun onTimeChanged(isBegin: Boolean, cal: Calendar) {
        if (isBegin) {
            begin = (cal.timeInMillis / 1000).toInt()
            val timestamp = begin.toString()
            timer = timer.copy(
                begin = timestamp,
                beginReadable = DateTime.getYearDateTimeString(timestamp)
            )
        } else {
            end = (cal.timeInMillis / 1000).toInt()
            val timestamp = end.toString()
            timer = timer.copy(
                end = timestamp,
                endReadable = DateTime.getYearDateTimeString(timestamp)
            )
        }
        editState.setBeginEndLabels(begin, end)
    }
}
