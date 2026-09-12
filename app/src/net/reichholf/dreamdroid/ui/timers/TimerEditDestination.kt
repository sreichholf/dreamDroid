package net.reichholf.dreamdroid.ui.timers

import android.app.Activity
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.MenuProvider
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.launchLocationsAndTagsLoad
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.Tag
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.ui.nav.NavExtras
import java.util.Calendar
import java.util.Collections

private const val LOG_TAG = "TimerEditDestination"

/**
 * Phase 2.7g: timer create/edit as a direct Compose NavHost destination.
 * Working copy lives on [PhoneNavHostFragment] so service-pick navigation does not wipe edits.
 */
@Composable
fun TimerEditDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val remount = hostFragment.timerEditRemountEpoch
    val tag = hostFragment.timerEditRouteTag()
    val session = remember(tag, remount) {
        hostFragment.obtainTimerEditSession(tag, remount)
    }
    var showRepeatingsPicker by remember { mutableStateOf(false) }
    var showTagsPicker by remember { mutableStateOf(false) }

    DisposableEffect(hostFragment, session, tag, remount) {
        hostFragment.composeActivityResultListener = session
        val activity = context as? AppCompatActivity
        activity?.title = context.getString(R.string.timer)
        activity?.addMenuProvider(session, hostFragment.viewLifecycleOwner)
        onDispose {
            if (hostFragment.composeActivityResultListener === session) {
                hostFragment.composeActivityResultListener = null
            }
            activity?.removeMenuProvider(session)
            session.dismissProgress()
        }
    }

    LaunchedEffect(tag, remount) {
        session.hostFragment = hostFragment
        session.context = context
        session.ensureLocationsAndTagsThenReload()
    }

    TimerEditScreen(
        state = session.editState,
        saveLabel = context.getString(R.string.save),
        onSave = { session.saveTimer() },
        onPickBeginDate = { session.pickBeginDate() },
        onPickBeginTime = { session.pickBeginTime() },
        onPickEndDate = { session.pickEndDate() },
        onPickEndTime = { session.pickEndTime() },
        onPickRepeated = { showRepeatingsPicker = true },
        onPickService = { session.pickService() },
        onPickTags = { showTagsPicker = true },
        modifier = modifier,
    )

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
            },
        )
    }
    if (showTagsPicker) {
        val tags = DreamDroid.getTags().map { it.toString() }
        val checked = BooleanArray(tags.size) { i -> session.selectedTags.contains(DreamDroid.getTags()[i]) }
        MultiChoiceAlertDialog(
            title = stringResource(R.string.choose_tags),
            items = tags,
            initialChecked = checked,
            onDismiss = { showTagsPicker = false },
            onConfirm = { indices ->
                session.applyTagsSelection(indices)
                showTagsPicker = false
            },
        )
    }

    IndeterminateProgressHost(session.progress)
}

/**
 * Mutable timer-edit working copy. Held on the NavHost so leaving composition for
 * [PhoneNavRoutes.TIMER_SERVICE_PICK] keeps form state.
 */
class TimerEditSession(
    val routeTag: String,
    val remountEpoch: Int,
    var timer: ExtendedHashMap,
    var timerOld: ExtendedHashMap?,
    var isCreate: Boolean,
    val selectedTags: ArrayList<String>,
    val checkedDays: BooleanArray,
) : PhoneNavHostFragment.ActivityResultListener,
    MenuProvider {

    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    val editState = TimerEditState()
    var begin: Int = 0
    var end: Int = 0
    private var tagsChanged = false
    var progress by mutableStateOf<IndeterminateProgressState?>(null)
    private var locationsJob: kotlinx.coroutines.Job? = null
    private var saveJob: kotlinx.coroutines.Job? = null

    fun dismissProgress() {
        progress = null
        locationsJob?.cancel()
        locationsJob = null
        saveJob?.cancel()
        saveJob = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.save, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            Statics.ITEM_SAVE -> {
                saveTimer()
                true
            }
            Statics.ITEM_CANCEL -> {
                hostFragment?.deliverPickResult(Activity.RESULT_CANCELED, null)
                true
            }
            else -> false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != Statics.REQUEST_PICK_SERVICE || resultCode != Activity.RESULT_OK) {
            return
        }
        @Suppress("DEPRECATION")
        val map = data?.getSerializableExtra(NavExtras.DATA) as? ExtendedHashMap ?: return
        timer.put(Timer.KEY_SERVICE_NAME, map.getString(Service.KEY_NAME))
        timer.put(Timer.KEY_REFERENCE, map.getString(Service.KEY_REFERENCE))
        editState.serviceName = timer.getString(Timer.KEY_SERVICE_NAME).orEmpty()
    }

    fun pickService() {
        hostFragment?.navigateToTimerServicePick()
    }

    fun applyRepeatingsSelection(indices: List<Int>) {
        java.util.Arrays.fill(checkedDays, false)
        for (which in indices) {
            if (which in checkedDays.indices) {
                checkedDays[which] = true
            }
        }
        editState.repeatedLabel = setRepeated(checkedDays)
    }

    fun applyTagsSelection(indices: List<Int>) {
        val tags = DreamDroid.getTags()
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
            timer.put(Timer.KEY_TAGS, joined)
            editState.tagsLabel = joined
        }
    }

    fun pickBeginDate() {
        val ctx = context ?: return
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(calendar(begin).timeInMillis)
            .build()
        picker.addOnPositiveButtonClickListener {
            onDateSet(true, picker.selection as Long)
        }
        (ctx as MultiPaneHandler).showDialogFragment(picker, "dialog_pick_begin_date")
    }

    fun pickBeginTime() {
        val ctx = context ?: return
        val cal = calendar(begin)
        val timeFormat = if (DateFormat.is24HourFormat(ctx)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
        val picker = MaterialTimePicker.Builder()
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTimeFormat(timeFormat)
            .build()
        picker.addOnPositiveButtonClickListener {
            onTimeSet(true, picker.hour, picker.minute)
        }
        (ctx as MultiPaneHandler).showDialogFragment(picker, "dialog_pick_begin_time")
    }

    fun pickEndDate() {
        val ctx = context ?: return
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(calendar(end).timeInMillis)
            .build()
        picker.addOnPositiveButtonClickListener {
            onDateSet(false, picker.selection as Long)
        }
        (ctx as MultiPaneHandler).showDialogFragment(picker, "dialog_pick_end_date")
    }

    fun pickEndTime() {
        val ctx = context ?: return
        val cal = calendar(end)
        val timeFormat = if (DateFormat.is24HourFormat(ctx)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
        val picker = MaterialTimePicker.Builder()
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTimeFormat(timeFormat)
            .build()
        picker.addOnPositiveButtonClickListener {
            onTimeSet(false, picker.hour, picker.minute)
        }
        (ctx as MultiPaneHandler).showDialogFragment(picker, "dialog_pick_end_time")
    }

    fun ensureLocationsAndTagsThenReload() {
        val host = hostFragment ?: return
        if (DreamDroid.getLocations().size == 0 || DreamDroid.getTags().size == 0) {
            if (locationsJob != null) {
                return
            }
            locationsJob = host.launchLocationsAndTagsLoad(
                onProgress = { title, progressText ->
                    progress = IndeterminateProgressState(title = title, message = progressText)
                },
                onReady = {
                    locationsJob = null
                    progress = null
                    reload()
                },
            )
        } else {
            reload()
        }
    }

    fun reload() {
        val ctx = context ?: return
        begin = DateTime.parseTimestamp(timer.getString(Timer.KEY_BEGIN))
        end = DateTime.parseTimestamp(timer.getString(Timer.KEY_END))
        var repeatedValue = 0
        try {
            repeatedValue = DateTime.parseTimestamp(timer.getString(Timer.KEY_REPEATED))
        } catch (_: NumberFormatException) {
        }
        val repeatedText = getRepeated(repeatedValue)
        val text = timer.getString(Timer.KEY_TAGS).orEmpty()
        selectedTags.clear()
        if (text.isNotEmpty()) {
            Collections.addAll(selectedTags, *text.split(" ").toTypedArray())
        }
        val afterEvents = ctx.resources.getTextArray(R.array.afterevents).map { it.toString() }
        editState.loadFrom(timer, afterEvents, DreamDroid.getLocations(), repeatedText)
    }

    fun saveTimer() {
        val host = hostFragment ?: return
        val ctx = context ?: return
        Log.i(LOG_TAG, "saveTimer()")
        progress = IndeterminateProgressState(message = ctx.getString(R.string.saving))
        editState.applyTo(timer)
        val params = Timer.getSaveParams(timer, timerOld)
        saveJob?.cancel()
        saveJob = host.launchSimpleResultLoad(TimerChangeRequestHandler(), params) { _, result, _ ->
            progress = null
            if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
                host.clearTimerEditSession()
                host.deliverPickResult(Activity.RESULT_OK, null)
            }
        }
    }

    private fun getRepeated(value: Int): String {
        val ctx = context ?: return ""
        var remaining = value
        var text = ""
        val daysShort = ctx.resources.getTextArray(R.array.weekdays_short)
        for (i in REPEATED_VALUES.indices) {
            val checked = (remaining and 1) == 1
            if (checked) {
                if (text.isNotEmpty()) text = text.plus(", ")
                text = text.plus(daysShort[i].toString())
            }
            checkedDays[i] = checked
            remaining = remaining shr 1
        }
        return text.ifEmpty { ctx.getText(R.string.none).toString() }
    }

    private fun setRepeated(days: BooleanArray): String {
        val ctx = context ?: return ""
        var text = ""
        var value = 0
        val daysShort = ctx.resources.getTextArray(R.array.weekdays_short)
        for (i in days.indices) {
            if (days[i]) {
                if (text.isNotEmpty()) text = text.plus(", ")
                text = text.plus(daysShort[i].toString())
                value += REPEATED_VALUES[i]
            }
        }
        timer.put(Timer.KEY_REPEATED, value.toString())
        text = when (value) {
            31 -> ctx.getText(R.string.mo_to_fr).toString()
            127 -> ctx.getText(R.string.daily).toString()
            else -> text
        }
        return text.ifEmpty { ctx.getText(R.string.none).toString() }
    }

    private fun calendar(time: Int): Calendar = Calendar.getInstance().apply {
        timeInMillis = time.toLong() * 1000
    }

    private fun onDateSet(isBegin: Boolean, millis: Long) {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        onDateSet(isBegin, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }

    private fun onDateSet(isBegin: Boolean, year: Int, month: Int, day: Int) {
        val time = if (isBegin) begin else end
        val cal = calendar(time)
        if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month && cal.get(Calendar.DATE) == day) {
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
            timer.put(Timer.KEY_BEGIN, timestamp)
            timer.put(Timer.KEY_BEGIN_READEABLE, DateTime.getYearDateTimeString(timestamp))
        } else {
            end = (cal.timeInMillis / 1000).toInt()
            val timestamp = end.toString()
            timer.put(Timer.KEY_END, timestamp)
            timer.put(Timer.KEY_END_READABLE, DateTime.getYearDateTimeString(timestamp))
        }
        editState.setBeginEndLabels(begin, end)
    }


    companion object {
        private val REPEATED_VALUES = intArrayOf(1, 2, 4, 8, 16, 32, 64)

        const val STATE_TIMER = "timer_edit_session_timer"
        const val STATE_TIMER_OLD = "timer_edit_session_timer_old"
        const val STATE_TAGS = "timer_edit_session_tags"
        const val STATE_CREATE = "timer_edit_session_create"
        const val STATE_CHECKED = "timer_edit_session_checked"
        const val STATE_TAG = "timer_edit_session_tag"
        const val STATE_REMOUNT = "timer_edit_session_remount"

        fun fromArgs(args: android.os.Bundle, routeTag: String, remountEpoch: Int): TimerEditSession {
            @Suppress("DEPRECATION")
            val data = (args.getSerializable(NavExtras.DATA) as? ExtendedHashMap)?.clone()
                ?: ExtendedHashMap()
            @Suppress("UNCHECKED_CAST")
            val timer = ((data["timer"] as? ExtendedHashMap) ?: ExtendedHashMap()).clone()
            val isCreate = !Intent.ACTION_EDIT.equals(data["action"])
            val timerOld = if (isCreate) null else timer.clone()
            return TimerEditSession(
                routeTag = routeTag,
                remountEpoch = remountEpoch,
                timer = timer,
                timerOld = timerOld,
                isCreate = isCreate,
                selectedTags = ArrayList(),
                checkedDays = BooleanArray(7),
            )
        }

        fun fromSavedState(state: android.os.Bundle): TimerEditSession? {
            val routeTag = state.getString(STATE_TAG) ?: return null
            @Suppress("DEPRECATION")
            val timer = state.getSerializable(STATE_TIMER) as? ExtendedHashMap ?: return null
            @Suppress("DEPRECATION")
            val timerOld = state.getSerializable(STATE_TIMER_OLD) as? ExtendedHashMap
            val tags = state.getStringArrayList(STATE_TAGS) ?: ArrayList()
            val checked = state.getBooleanArray(STATE_CHECKED) ?: BooleanArray(7)
            return TimerEditSession(
                routeTag = routeTag,
                remountEpoch = state.getInt(STATE_REMOUNT, 0),
                timer = timer,
                timerOld = timerOld,
                isCreate = state.getBoolean(STATE_CREATE, timerOld == null),
                selectedTags = ArrayList(tags),
                checkedDays = checked,
            )
        }
    }

    fun writeTo(outState: android.os.Bundle) {
        outState.putString(STATE_TAG, routeTag)
        outState.putInt(STATE_REMOUNT, remountEpoch)
        outState.putSerializable(STATE_TIMER, timer)
        if (timerOld != null) {
            outState.putSerializable(STATE_TIMER_OLD, timerOld)
        }
        outState.putStringArrayList(STATE_TAGS, selectedTags)
        outState.putBoolean(STATE_CREATE, isCreate)
        outState.putBooleanArray(STATE_CHECKED, checkedDays)
    }
}
