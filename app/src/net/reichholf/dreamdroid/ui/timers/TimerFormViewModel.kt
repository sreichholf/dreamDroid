package net.reichholf.dreamdroid.ui.timers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.VpsMode
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * One timer being created or edited. [timer] is the working copy apart from its title and
 * description, which are [TimerFormViewModel.name] and [TimerFormViewModel.description];
 * it is null until an editor opens. [locations] and [tags] are what the receiver offers;
 * [vpsPlugin] is whether it has the VPS plugin, which the VPS field needs.
 * [progress] names the running request, which blocks another. [finished] is set once a
 * save or delete went through.
 */
data class TimerEditUiState(
    val timer: Timer? = null,
    val isCreate: Boolean = true,
    val locations: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val vpsPlugin: Boolean = false,
    val progress: UiText? = null,
    val saveError: UiText? = null,
    val mutationsBlocked: Boolean = false,
    val finished: Boolean = false
) {
    val title: UiText
        get() = UiText.Resource(R.string.timer)

    /** The form of [timer]; [name] is the title as typed ([TimerFormViewModel.name]). */
    fun form(name: CharSequence): TimerEditForm? =
        timer?.let { TimerEditForm.from(it, locations, vpsPlugin, name) }
}

/**
 * The timer editor shared by the phone and TV destinations. With a [handle], the working
 * copy and the typed fields survive process death.
 */
abstract class TimerFormViewModel(
    protected val timers: TimerRepository,
    private val sessions: SessionConnectionHolder,
    private val handle: SavedStateHandle?
) : ViewModel(),
    TimerFormActions {
    private val _uiState = MutableStateFlow(
        TimerEditUiState(mutationsBlocked = sessions.status.value.blocksMutations)
    )
    val uiState: StateFlow<TimerEditUiState> = _uiState.asStateFlow()

    val name = SavedTextField(viewModelScope, handle, KEY_NAME)
    val description = SavedTextField(viewModelScope, handle, KEY_DESCRIPTION)

    /** The timer as the receiver has it; null while creating. */
    private var original: Timer? = handle?.get<Timer>(KEY_ORIGINAL)

    private var choicesJob: Job? = null
    private var requestJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(mutationsBlocked = status.blocksMutations) }
            }
        }
    }

    /** Starts editing [timer], dropping any earlier working copy. */
    protected fun load(timer: Timer, isCreate: Boolean) {
        cancelWork()
        original = if (isCreate) null else timer
        handle?.set(KEY_ORIGINAL, original)
        handle?.set(KEY_CREATE, isCreate)
        name.set(timer.name)
        description.set(timer.description)
        _uiState.update {
            it.copy(
                timer = timer,
                isCreate = isCreate,
                progress = null,
                saveError = null,
                finished = false
            )
        }
        handle?.set(KEY_TIMER, timer)
        loadChoices()
    }

    /** Shows the working copy [handle] kept; false when there is none. */
    protected fun restore(): Boolean {
        val timer = handle?.get<Timer>(KEY_TIMER) ?: return false
        val isCreate = handle.get<Boolean>(KEY_CREATE) ?: (original == null)
        _uiState.update { it.copy(timer = timer, isCreate = isCreate) }
        loadChoices()
        return true
    }

    /** Stops editing and drops the saved working copy; the next [load] starts fresh. */
    protected fun close() {
        cancelWork()
        original = null
        handle?.remove<Timer>(KEY_TIMER)
        handle?.remove<Timer>(KEY_ORIGINAL)
        handle?.remove<Boolean>(KEY_CREATE)
        _uiState.update {
            it.copy(timer = null, progress = null, saveError = null, finished = false)
        }
    }

    override fun onEnabledChange(enabled: Boolean) = edit {
        copy(disabled = if (enabled) "0" else "1")
    }

    override fun onZapChange(zap: Boolean) = edit { copy(justPlay = if (zap) "1" else "0") }

    override fun onAfterEventChange(index: Int) = edit { copy(afterEvent = index.toString()) }

    override fun onLocationChange(index: Int) {
        val location = _uiState.value.locations.getOrNull(index) ?: return
        edit { copy(location = location) }
    }

    override fun onRepeatedChange(days: List<Int>) = edit {
        copy(repeated = timerRepeatedValue(days).toString())
    }

    override fun onTagsChange(indices: List<Int>) {
        val offered = _uiState.value.tags
        val picked = indices.sorted().mapNotNull { offered.getOrNull(it) }
        edit { copy(tags = picked.joinToString(" ")) }
    }

    override fun onDatePicked(isBegin: Boolean, utcDateMillis: Long) = edit {
        withDate(isBegin, utcDateMillis)
    }

    override fun onTimePicked(isBegin: Boolean, hourOfDay: Int, minute: Int) = edit {
        withClock(isBegin, hourOfDay, minute)
    }

    override fun onVpsModeChange(mode: VpsMode) {
        val typedName = name.text
        edit { withVpsMode(mode, typedName) }
    }

    override fun onVpsDatePicked(utcDateMillis: Long) = edit { withVpsDate(utcDateMillis) }

    override fun onVpsTimePicked(hourOfDay: Int, minute: Int) = edit {
        withVpsClock(hourOfDay, minute)
    }

    fun onServicePicked(service: Service) = edit {
        copy(serviceName = service.name, reference = service.reference)
    }

    /** The timer as edited so far, typed fields included; null while not editing. */
    private fun workingCopy(): Timer? =
        _uiState.value.timer?.copy(name = name.text, description = description.text)

    /** Sends the working copy to the receiver. Ignored while blocked or busy. */
    fun save() {
        val state = _uiState.value
        val edited = workingCopy()?.normalized(state.locations, state.vpsPlugin) ?: return
        edit { edited }
        val replaced = original
        request(R.string.saving) { timers.save(edited, replaced) }
    }

    /** Deletes the timer on the receiver. Creating has nothing to delete. */
    fun delete() {
        val state = _uiState.value
        val timer = state.timer ?: return
        if (state.isCreate) {
            return
        }
        val deleted = original ?: timer
        request(R.string.deleting) { timers.delete(deleted) }
    }

    fun onFinishHandled() {
        _uiState.update { it.copy(finished = false) }
    }

    /** Runs a receiver mutation; its success sets [TimerEditUiState.finished]. */
    protected fun request(progress: Int, call: suspend () -> EnigmaResponse<SimpleResult>) {
        val state = _uiState.value
        if (state.timer == null || state.progress != null || state.mutationsBlocked) {
            return
        }
        _uiState.update { it.copy(progress = UiText.Resource(progress), saveError = null) }
        requestJob = viewModelScope.launch {
            val response = call()
            _uiState.update {
                if (response.value?.state == Python.TRUE) {
                    it.copy(progress = null, finished = true)
                } else {
                    it.copy(progress = null, saveError = response.userMessageText())
                }
            }
        }
    }

    private fun edit(change: Timer.() -> Timer) {
        val timer = _uiState.value.timer ?: return
        val next = timer.change()
        _uiState.update { it.copy(timer = next) }
        handle?.set(KEY_TIMER, next)
    }

    /**
     * Fetches locations, tags and whether the receiver has the VPS plugin; a new timer without
     * VPS then gets the profile's VPS default. Known ones answer without suspending, so the
     * progress only shows while the receiver is asked.
     */
    private fun loadChoices() {
        var done = false
        choicesJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val choices = timers.locationsAndTags()
            val vpsPlugin = timers.hasVpsPlugin()
            val state = _uiState.value
            if (vpsPlugin && state.isCreate && state.timer?.vps == null) {
                timers.vpsForNewTimer()?.let { vps ->
                    edit { if (this.vps == null) copy(vps = vps) else this }
                }
            }
            done = true
            _uiState.update {
                it.copy(
                    locations = choices.locations,
                    tags = choices.tags,
                    vpsPlugin = vpsPlugin,
                    progress = null
                )
            }
        }
        if (!done) {
            _uiState.update { it.copy(progress = UiText.Resource(R.string.loading)) }
        }
    }

    private fun cancelWork() {
        choicesJob?.cancel()
        choicesJob = null
        requestJob?.cancel()
        requestJob = null
    }

    private companion object {
        const val KEY_TIMER = "timer_edit_timer"
        const val KEY_ORIGINAL = "timer_edit_original"
        const val KEY_CREATE = "timer_edit_create"
        const val KEY_NAME = "timer_edit_name"
        const val KEY_DESCRIPTION = "timer_edit_description"
    }
}
