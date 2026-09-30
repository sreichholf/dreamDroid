package net.reichholf.dreamdroid.ui.autotimer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AutoTimerLoad
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.AutoTimerWriteResult
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerSettings
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.ClockWindow
import net.reichholf.dreamdroid.enigma.autotimer.DateWindow
import net.reichholf.dreamdroid.enigma.autotimer.DayFilter
import net.reichholf.dreamdroid.enigma.autotimer.RecordMode
import net.reichholf.dreamdroid.enigma.autotimer.SearchType
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.enigma.autotimer.groups
import net.reichholf.dreamdroid.ui.nav.AutoTimerEdit
import net.reichholf.dreamdroid.ui.nav.AutoTimerPreview
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

/** A field of the editor that opens a picker dialog. */
enum class AutoTimerEditPick {
    TimeFrom,
    TimeTo,
    DateAfter,
    DateBefore
}

sealed interface AutoTimerEditContent {
    data object Loading : AutoTimerEditContent

    data object Editing : AutoTimerEditContent

    /** The box changed its AutoTimers since the form loaded; nothing was written. */
    data object Changed : AutoTimerEditContent

    /** The id no longer names this AutoTimer, or dreamDroid cannot read it. */
    data object Gone : AutoTimerEditContent

    data object PluginMissing : AutoTimerEditContent

    data class Failed(val message: UiText) : AutoTimerEditContent
}

/**
 * The AutoTimer editor. [draft] holds everything but the match and name, which are text
 * fields. [loaded] is the AutoTimer being edited, null when creating; [base] is what a save
 * compares against: [loaded]'s settings or the box's defaults. [saved] asks the screen to show
 * the saved AutoTimer.
 */
data class AutoTimerEditUiState(
    val isCreate: Boolean,
    val content: AutoTimerEditContent = AutoTimerEditContent.Loading,
    val base: AutoTimerSettings = AutoTimerSettings.NEW,
    val draft: AutoTimerSettings = AutoTimerSettings.NEW,
    val loaded: AutoTimer? = null,
    val blocked: Boolean = false,
    val saving: Boolean = false,
    val picker: AutoTimerEditPick? = null,
    val matchError: UiText? = null,
    val userMessage: UiText? = null,
    val saved: AutoTimerPreview? = null
) {
    val title: UiText
        get() = UiText.Resource(if (isCreate) R.string.autotimer_new else R.string.autotimer_edit)

    val editable: Boolean
        get() = content == AutoTimerEditContent.Editing && !saving
}

/**
 * Creates an AutoTimer from the box's defaults, or edits one. The route names the AutoTimer
 * by id and name, since the box may have renumbered them. A save sends only what changed; the
 * form and the draft survive process death.
 */
@HiltViewModel
class AutoTimerEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val autoTimers: AutoTimerRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel(),
    AutoTimerEditActions {
    private val routeId = savedStateHandle.get<Int>(AutoTimerEdit::id.name) ?: NEW_ID
    private val routeName = savedStateHandle.get<String>(AutoTimerEdit::name.name).orEmpty()

    private val _uiState = MutableStateFlow(
        AutoTimerEditUiState(
            isCreate = routeId == NEW_ID,
            blocked = sessions.status.value.blocksMutations
        )
    )
    val uiState: StateFlow<AutoTimerEditUiState> = _uiState.asStateFlow()

    val match = SavedTextField(viewModelScope, savedStateHandle, KEY_MATCH) {
        _uiState.update { it.copy(matchError = null) }
    }
    val name = SavedTextField(viewModelScope, savedStateHandle, KEY_NAME)

    private var loadJob: Job? = null

    /** The API carries no time zone; days are taken in the phone's. */
    private val zone: ZoneId = ZoneId.systemDefault()

    init {
        viewModelScope.launch {
            sessions.status.map { it.blocksMutations }.distinctUntilChanged().collect { blocked ->
                _uiState.update { it.copy(blocked = blocked) }
            }
        }
        if (!restore()) {
            reload()
        }
    }

    /** Loads the AutoTimer (or the defaults) from the box again; drops the draft. */
    override fun reload() {
        loadJob?.cancel()
        _uiState.update { it.copy(content = AutoTimerEditContent.Loading, picker = null) }
        loadJob = viewModelScope.launch {
            val load = autoTimers.list()
            if (load !is AutoTimerLoad.Ready) {
                val content = when (load) {
                    AutoTimerLoad.PluginMissing -> AutoTimerEditContent.PluginMissing
                    is AutoTimerLoad.Failed -> AutoTimerEditContent.Failed(load.message)
                    is AutoTimerLoad.Ready -> error("unreachable")
                }
                _uiState.update { it.copy(content = content) }
                return@launch
            }
            if (_uiState.value.isCreate) {
                start(loaded = null, base = load.defaults ?: AutoTimerSettings.NEW)
            } else {
                val loaded = (
                    load.entries.firstOrNull { it.id.value == routeId }
                        as? AutoTimerEntry.Readable
                    )?.autoTimer?.takeIf {
                    it.settings.name == routeName
                }
                if (loaded == null) {
                    _uiState.update { it.copy(content = AutoTimerEditContent.Gone) }
                } else {
                    start(loaded, loaded.settings)
                }
            }
        }
    }

    override fun setSearchType(type: SearchType) = edit { copy(searchType = type) }

    override fun setCaseSensitive(sensitive: Boolean) = edit { copy(caseSensitive = sensitive) }

    override fun setEnabled(enabled: Boolean) = edit { copy(enabled = enabled) }

    /** Zapping again brings back the loaded "set end time", or the plugin's default. */
    override fun setZap(zap: Boolean) {
        val loadedZap = _uiState.value.base.recordMode as? RecordMode.Zap
        edit {
            copy(
                recordMode = if (zap) {
                    loadedZap ?: RecordMode.Zap(
                        setEndTime = true
                    )
                } else {
                    RecordMode.Record
                }
            )
        }
    }

    override fun removeTarget(target: Target) = edit { copy(targets = targets - target) }

    /** Adds the picker's bouquets and channels that the AutoTimer does not have yet. */
    fun addTargets(picked: List<Target>) = edit {
        copy(targets = targets + picked.filter { new -> targets.none { it.ref == new.ref } })
    }

    override fun setTimeWindow(on: Boolean) = edit {
        copy(timeWindow = if (on) timeWindow ?: DEFAULT_TIME_WINDOW else null)
    }

    override fun toggleDay(day: DayFilter) = edit {
        val days = include.days
        copy(include = include.copy(days = if (day in days) days - day else days + day))
    }

    /** Days start at local midnight; the plugin matches events that begin in between. */
    override fun setDateWindow(on: Boolean) = edit {
        val today = LocalDate.now(zone)
        copy(
            dateWindow = if (on) {
                dateWindow ?: DateWindow(
                    today.atStartOfDay(zone).toInstant(),
                    today.plusDays(DEFAULT_DATE_DAYS).atStartOfDay(zone).toInstant()
                )
            } else {
                null
            }
        )
    }

    override fun openPicker(pick: AutoTimerEditPick) {
        if (_uiState.value.editable) {
            _uiState.update { it.copy(picker = pick) }
        }
    }

    override fun dismissPicker() {
        _uiState.update { it.copy(picker = null) }
    }

    override fun onTimePicked(hour: Int, minute: Int) {
        val pick = _uiState.value.picker
        dismissPicker()
        val time = LocalTime.of(hour, minute)
        edit {
            val window = timeWindow ?: DEFAULT_TIME_WINDOW
            copy(
                timeWindow = when (pick) {
                    AutoTimerEditPick.TimeFrom -> window.copy(from = time)
                    AutoTimerEditPick.TimeTo -> window.copy(to = time)
                    else -> window
                }
            )
        }
    }

    /**
     * [utcDateMillis] is midnight UTC of the picked day, as the Material date picker gives it.
     * The window gets local midnight of that day, as the plugin's own editor sets it: events
     * must begin after the first day starts and before the last day starts.
     */
    override fun onDatePicked(utcDateMillis: Long) {
        val pick = _uiState.value.picker
        dismissPicker()
        val day = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant()
        edit {
            val window = dateWindow ?: return@edit this
            copy(
                dateWindow = when (pick) {
                    AutoTimerEditPick.DateAfter -> window.copy(after = day)
                    AutoTimerEditPick.DateBefore -> window.copy(before = day)
                    else -> window
                }
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.editable || state.blocked) {
            return
        }
        val matchText = match.text
        if (matchText.isBlank()) {
            _uiState.update {
                it.copy(matchError = UiText.Resource(R.string.autotimer_match_empty))
            }
            return
        }
        val edited = state.draft.copy(match = matchText, name = name.text.trim())
        val loaded = state.loaded
        val write = if (loaded != null) {
            AutoTimerWrite.Change(loaded, edited)
        } else {
            AutoTimerWrite.Create(state.base, edited)
        }
        if (loaded != null && write.groups.isEmpty() && edited.match == loaded.settings.match &&
            edited.name == loaded.settings.name
        ) {
            _uiState.update { it.copy(saved = AutoTimerPreview(loaded.id.value, edited.name)) }
            return
        }
        _uiState.update { it.copy(saving = true, picker = null) }
        loadJob = viewModelScope.launch {
            when (val result = autoTimers.save(write)) {
                is AutoTimerWriteResult.Done -> {
                    val saved = autoTimers.locate(edited, loaded?.id)
                    _uiState.update {
                        it.copy(
                            saving = false,
                            saved = saved?.let { at ->
                                AutoTimerPreview(at.id.value, at.settings.name)
                            },
                            userMessage = if (saved == null) result.message else it.userMessage
                        )
                    }
                }

                AutoTimerWriteResult.Conflict -> _uiState.update {
                    it.copy(saving = false, content = AutoTimerEditContent.Changed)
                }

                is AutoTimerWriteResult.Failed -> _uiState.update {
                    it.copy(saving = false, userMessage = result.message)
                }
            }
        }
    }

    fun onSavedHandled() {
        _uiState.update { it.copy(saved = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun start(loaded: AutoTimer?, base: AutoTimerSettings) {
        savedStateHandle[KEY_LOADED] = loaded
        savedStateHandle[KEY_BASE] = base
        savedStateHandle[KEY_DRAFT] = base
        match.set(base.match)
        name.set(if (loaded != null) base.name else "")
        _uiState.update {
            it.copy(
                content = AutoTimerEditContent.Editing,
                loaded = loaded,
                base = base,
                draft = base,
                matchError = null
            )
        }
    }

    private fun restore(): Boolean {
        val base = savedStateHandle.get<AutoTimerSettings>(KEY_BASE) ?: return false
        val draft = savedStateHandle.get<AutoTimerSettings>(KEY_DRAFT) ?: base
        _uiState.update {
            it.copy(
                content = AutoTimerEditContent.Editing,
                loaded = savedStateHandle.get<AutoTimer>(KEY_LOADED),
                base = base,
                draft = draft
            )
        }
        return true
    }

    private fun edit(change: AutoTimerSettings.() -> AutoTimerSettings) {
        if (!_uiState.value.editable) {
            return
        }
        val next = _uiState.value.draft.change()
        savedStateHandle[KEY_DRAFT] = next
        _uiState.update { it.copy(draft = next) }
    }

    companion object {
        /** [AutoTimerEdit.id] of a new AutoTimer. */
        const val NEW_ID = -1

        private val DEFAULT_TIME_WINDOW = ClockWindow(LocalTime.of(20, 0), LocalTime.of(23, 0))
        private const val DEFAULT_DATE_DAYS = 30L
        private const val KEY_MATCH = "autotimer_edit_match"
        private const val KEY_NAME = "autotimer_edit_name"
        private const val KEY_LOADED = "autotimer_edit_loaded"
        private const val KEY_BASE = "autotimer_edit_base"
        private const val KEY_DRAFT = "autotimer_edit_draft"
    }
}
