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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.Collections
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.enigma.simpleResultFromFetch
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Tag
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler
import net.reichholf.dreamdroid.helpers.getSerializableCompat
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.ui.compose.inflateSaveAndDelete
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.ui.epg.EpgDatePickerDialog
import net.reichholf.dreamdroid.ui.epg.EpgTimePickerDialog
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly

private const val LOG_TAG = "TimerEditDestination"

/**
 * Timer create/edit as a Compose NavHost destination.
 * The working copy lives on [TimerEditViewModel] so service-pick navigation keeps the form.
 */
@Composable
fun TimerEditDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: TimerEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val remount = handle.timerEditRemountEpoch
    val tag = handle.timerEditRouteTag()
    LaunchedEffect(tag, remount) {
        viewModel.start(handle)
        val current = viewModel.session ?: return@LaunchedEffect
        current.handle = handle
        current.context = context
        if (current.deliverSuccessIfAttached()) {
            return@LaunchedEffect
        }
        current.ensureLocationsAndTagsThenReload()
    }
    val session = viewModel.session ?: return
    var showRepeatingsPicker by remember { mutableStateOf(false) }
    var showTagsPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pickerKind by remember { mutableStateOf<TimerEditPicker?>(null) }
    val is24Hour = DateFormat.is24HourFormat(context)

    DisposableEffect(handle, session, tag, remount, viewModel) {
        session.handle = handle
        session.context = context
        session.onRequestDeleteConfirm = { showDeleteConfirm = true }
        handle.composeActivityResultListener = session
        val activity = context as? AppCompatActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.persist()
            }
        }
        activity?.title = context.getString(R.string.timer)
        activity?.lifecycle?.addObserver(observer)
        activity?.addMenuProvider(session)
        onDispose {
            if (handle.composeActivityResultListener === session) {
                handle.composeActivityResultListener = null
            }
            if (session.handle === handle) {
                session.handle = null
            }
            if (session.context === context) {
                session.context = null
            }
            session.onRequestDeleteConfirm = null
            activity?.lifecycle?.removeObserver(observer)
            activity?.removeMenuProvider(session)
            viewModel.persistIfBound(tag, remount)
        }
    }

    LaunchedEffect(session.progress) {
        (context as? AppCompatActivity)?.invalidateOptionsMenu()
    }

    TimerEditScreen(
        state = session.editState,
        saveLabel = context.getString(R.string.save),
        onSave = { session.saveTimer() },
        onPickBeginDate = { pickerKind = TimerEditPicker.BeginDate },
        onPickBeginTime = { pickerKind = TimerEditPicker.BeginTime },
        onPickEndDate = { pickerKind = TimerEditPicker.EndDate },
        onPickEndTime = { pickerKind = TimerEditPicker.EndTime },
        onPickRepeated = { showRepeatingsPicker = true },
        onPickService = { session.pickService() },
        onPickTags = { showTagsPicker = true },
        showSaveFab = false,
        mutating = session.progress != null,
        modifier = modifier
    )

    if (showDeleteConfirm) {
        ConfirmAlertDialog(
            title = session.timer.name,
            message = stringResource(R.string.delete_confirm),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { session.deleteTimer() },
            confirmLabel = stringResource(R.string.delete),
            destructive = true
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
        val tags = DreamDroid.getTags()
        val checked =
            BooleanArray(tags.size) { i -> session.selectedTags.contains(DreamDroid.getTags()[i]) }
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
        TimerEditPicker.BeginDate -> EpgDatePickerDialog(
            initialTimeSec = session.begin,
            onDismiss = { pickerKind = null },
            onConfirm = { utcDateMillis ->
                session.applyPickedDate(isBegin = true, utcDateMillis = utcDateMillis)
                pickerKind = null
            }
        )

        TimerEditPicker.EndDate -> EpgDatePickerDialog(
            initialTimeSec = session.end,
            onDismiss = { pickerKind = null },
            onConfirm = { utcDateMillis ->
                session.applyPickedDate(isBegin = false, utcDateMillis = utcDateMillis)
                pickerKind = null
            }
        )

        TimerEditPicker.BeginTime -> EpgTimePickerDialog(
            initialTimeSec = session.begin,
            is24Hour = is24Hour,
            onDismiss = { pickerKind = null },
            onConfirm = { hour, minute ->
                session.applyPickedTime(isBegin = true, hourOfDay = hour, minute = minute)
                pickerKind = null
            }
        )

        TimerEditPicker.EndTime -> EpgTimePickerDialog(
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

    IndeterminateProgressHost(session.progress)
}

private enum class TimerEditPicker {
    BeginDate,
    BeginTime,
    EndDate,
    EndTime
}

/**
 * Mutable timer-edit working copy. [TimerEditViewModel] holds the instance so
 * leaving composition for timer service pick keeps the form.
 */
class TimerEditSession(
    val routeTag: String,
    val remountEpoch: Int,
    var timer: TypedTimer,
    var timerOld: TypedTimer?,
    var isCreate: Boolean,
    val selectedTags: ArrayList<String>,
    val checkedDays: BooleanArray
) : PhoneNavHandle.ActivityResultListener,
    MenuProvider {

    var handle: PhoneNavHandle? = null
    var context: android.content.Context? = null
    val editState = TimerEditState()
    var begin: Int = 0
    var end: Int = 0
    private var tagsChanged = false
    var progress by mutableStateOf<IndeterminateProgressState?>(null)
    var onRequestDeleteConfirm: (() -> Unit)? = null

    /** Set by [TimerEditViewModel] so prefetch and save survive leaving composition. */
    internal var workScope: CoroutineScope? = null
    internal var onWorkingCopyChanged: (() -> Unit)? = null
    private var locationsJob: Job? = null
    private var saveJob: Job? = null
    private var formHydrated = false
    private var pendingSuccess = false

    internal fun cancelWork() {
        progress = null
        locationsJob?.cancel()
        locationsJob = null
        saveJob?.cancel()
        saveJob = null
    }

    internal fun flushFormIntoTimer() {
        if (formHydrated) {
            timer = editState.applyTo(timer)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflateSaveAndDelete(
            menu,
            canDelete = !isCreate,
            actionsEnabled = progress == null
        )
    }

    override fun onPrepareMenu(menu: Menu) {
        val enabled = progress == null
        menu.findItem(Statics.ITEM_SAVE)?.isEnabled = enabled
        menu.findItem(Statics.ITEM_DELETE)?.isEnabled = enabled
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        Statics.ITEM_SAVE -> {
            saveTimer()
            true
        }

        Statics.ITEM_DELETE -> {
            requestDelete()
            true
        }

        Statics.ITEM_CANCEL -> {
            handle?.deliverPickResult(Activity.RESULT_CANCELED, null)
            true
        }

        else -> false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != Statics.REQUEST_PICK_SERVICE || resultCode != Activity.RESULT_OK) {
            return
        }
        val picked = data?.getSerializableExtraCompat<Service>(NavExtras.DATA) ?: return
        timer = timer.copy(serviceName = picked.name, reference = picked.reference)
        editState.serviceName = timer.serviceName
        notifyWorkingCopy()
    }

    fun pickService() {
        handle?.navigateToTimerServicePick()
    }

    fun applyRepeatingsSelection(indices: List<Int>) {
        java.util.Arrays.fill(checkedDays, false)
        for (which in indices) {
            if (which in checkedDays.indices) {
                checkedDays[which] = true
            }
        }
        editState.repeatedLabel = setRepeated(checkedDays)
        notifyWorkingCopy()
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
            timer = timer.copy(tags = joined)
            editState.tagsLabel = joined
        }
        notifyWorkingCopy()
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
        if (handle == null) {
            return
        }
        val scope = workScope ?: return
        if (DreamDroid.getLocations().size == 0 || DreamDroid.getTags().size == 0) {
            if (locationsJob != null) {
                return
            }
            val ctx = context ?: return
            locationsJob = scope.launch {
                val http = EnigmaHttp()
                try {
                    if (DreamDroid.getLocations().size == 0) {
                        progress = IndeterminateProgressState(
                            title = ctx.getString(R.string.loading),
                            message = ctx.getString(R.string.locations) + " - " +
                                ctx.getString(R.string.fetching_data)
                        )
                        withContext(Dispatchers.IO) {
                            DreamDroid.loadLocations(http)
                        }
                    }
                    if (DreamDroid.getTags().size == 0) {
                        progress = IndeterminateProgressState(
                            title = ctx.getString(R.string.loading),
                            message = ctx.getString(R.string.tags) + " - " +
                                ctx.getString(R.string.fetching_data)
                        )
                        withContext(Dispatchers.IO) {
                            DreamDroid.loadTags(http)
                        }
                    }
                } finally {
                    locationsJob = null
                    progress = null
                }
                reload()
            }
        } else {
            reload()
        }
    }

    fun reload() {
        val ctx = context ?: return
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
        val afterEvents = ctx.resources.getTextArray(R.array.afterevents).map { it.toString() }
        editState.loadFrom(timer, afterEvents, DreamDroid.getLocations(), repeatedText)
        formHydrated = true
        notifyWorkingCopy()
    }

    fun saveTimer() {
        if (progress != null) {
            return
        }
        val host = handle ?: return
        val ctx = context ?: return
        val scope = workScope ?: return
        host.runOnlineOnly {
            Log.i(LOG_TAG, "saveTimer()")
            editState.saveError = ""
            progress = IndeterminateProgressState(message = ctx.getString(R.string.saving))
            timer = editState.applyTo(timer)
            notifyWorkingCopy()
            val params = Timer.getSaveParams(timer, timerOld)
            val handler = TimerChangeRequestHandler()
            saveJob?.cancel()
            saveJob = scope.launch {
                val fetched = withContext(Dispatchers.IO) {
                    simpleResultFromFetch(handler.fetch(EnigmaHttp(), params)) { xml ->
                        handler.parseSimpleResult(xml)
                    }
                }
                onSaveResult(fetched.second)
            }
        }
    }

    fun requestDelete() {
        if (isCreate || progress != null) {
            return
        }
        onRequestDeleteConfirm?.invoke()
    }

    fun deleteTimer() {
        if (isCreate || progress != null) {
            return
        }
        val host = handle ?: return
        val ctx = context ?: return
        val scope = workScope ?: return
        val toDelete = timerOld ?: timer
        host.runOnlineOnly {
            Log.i(LOG_TAG, "deleteTimer()")
            editState.saveError = ""
            progress = IndeterminateProgressState(message = ctx.getString(R.string.deleting))
            val params = Timer.getDeleteParams(toDelete)
            val handler = TimerDeleteRequestHandler()
            saveJob?.cancel()
            saveJob = scope.launch {
                val fetched = withContext(Dispatchers.IO) {
                    simpleResultFromFetch(handler.fetch(EnigmaHttp(), params)) { xml ->
                        handler.parseSimpleResult(xml)
                    }
                }
                onSaveResult(fetched.second)
            }
        }
    }

    fun onSaveResult(result: SimpleResult) {
        progress = null
        if (Python.TRUE.equals(result.state)) {
            editState.saveError = ""
            pendingSuccess = true
            deliverSuccessIfAttached()
            return
        }
        val stateText = result.stateText
        editState.saveError = when {
            !stateText.isNullOrEmpty() -> stateText
            else -> context?.getString(R.string.get_content_error).orEmpty()
        }
    }

    /**
     * Pops the edit only while this session is the composed result listener.
     * A save that finishes during service pick waits until the form is showing again.
     */
    fun deliverSuccessIfAttached(): Boolean {
        if (!pendingSuccess) {
            return false
        }
        val host = handle
        if (host == null || host.composeActivityResultListener !== this) {
            return false
        }
        pendingSuccess = false
        host.deliverPickResult(Activity.RESULT_OK, null)
        return true
    }

    private fun notifyWorkingCopy() {
        onWorkingCopyChanged?.invoke()
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
        timer = timer.copy(repeated = value.toString())
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
        notifyWorkingCopy()
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

        fun fromArgs(
            args: android.os.Bundle,
            routeTag: String,
            remountEpoch: Int
        ): TimerEditSession {
            val timer = args.getSerializableCompat<TypedTimer>(NavExtras.DATA) ?: TypedTimer()
            val isCreate = !Intent.ACTION_EDIT.equals(args.getString(NavExtras.ACTION))
            val timerOld = if (isCreate) null else timer.copy()
            return TimerEditSession(
                routeTag = routeTag,
                remountEpoch = remountEpoch,
                timer = timer,
                timerOld = timerOld,
                isCreate = isCreate,
                selectedTags = ArrayList(),
                checkedDays = BooleanArray(7)
            )
        }

        fun fromSavedState(state: android.os.Bundle): TimerEditSession? {
            val routeTag = state.getString(STATE_TAG) ?: return null
            val timer = state.getSerializableCompat<TypedTimer>(STATE_TIMER) ?: return null
            val timerOld = state.getSerializableCompat<TypedTimer>(STATE_TIMER_OLD)
            val tags = state.getStringArrayList(STATE_TAGS) ?: ArrayList()
            val checked = state.getBooleanArray(STATE_CHECKED) ?: BooleanArray(7)
            return TimerEditSession(
                routeTag = routeTag,
                remountEpoch = state.getInt(STATE_REMOUNT, 0),
                timer = timer,
                timerOld = timerOld,
                isCreate = state.getBoolean(STATE_CREATE, timerOld == null),
                selectedTags = ArrayList(tags),
                checkedDays = checked
            )
        }
    }

    internal fun withRouteEpoch(remountEpoch: Int): TimerEditSession {
        if (this.remountEpoch == remountEpoch) {
            return this
        }
        return TimerEditSession(
            routeTag = routeTag,
            remountEpoch = remountEpoch,
            timer = timer,
            timerOld = timerOld,
            isCreate = isCreate,
            selectedTags = ArrayList(selectedTags),
            checkedDays = checkedDays.copyOf()
        )
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
