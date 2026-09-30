package net.reichholf.dreamdroid.ui.autotimer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AutoTimerPreviewLoad
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.AutoTimerWriteResult
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimer
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.PreviewMatch
import net.reichholf.dreamdroid.enigma.autotimer.Verdict
import net.reichholf.dreamdroid.ui.nav.AutoTimerPreview
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

sealed interface AutoTimerPreviewContent {
    data object Loading : AutoTimerPreviewContent

    /** The box previews enabled AutoTimers only. */
    data object Disabled : AutoTimerPreviewContent

    /** Events the AutoTimer would record, and those it found but skips, soonest first. */
    data class Ready(val upcoming: List<PreviewMatch>, val skipped: List<PreviewMatch>) :
        AutoTimerPreviewContent

    /** The id no longer names this AutoTimer on the receiver. */
    data object Gone : AutoTimerPreviewContent

    data object PluginMissing : AutoTimerPreviewContent

    data class PluginFailed(val message: String) : AutoTimerPreviewContent

    data class Failed(val message: UiText) : AutoTimerPreviewContent
}

/** The EPG for a match the sheet shows. */
sealed interface MatchEpg {
    data object Loading : MatchEpg

    data class Found(val event: Event) : MatchEpg

    /** Neither the cache nor the receiver has a programme starting then. */
    data object Missing : MatchEpg
}

/** The match an EPG sheet shows, and its programme once the lookup is done. */
data class AutoTimerMatchDetail(val match: PreviewMatch, val epg: MatchEpg = MatchEpg.Loading)

/**
 * The preview of one AutoTimer. [autoTimer] is the one the box listed for it; [expanded] are
 * the keys of skipped rows that show the plugin's log; [detail] is the upcoming match whose
 * EPG sheet is open.
 */
data class AutoTimerPreviewUiState(
    val name: String,
    val autoTimer: AutoTimer? = null,
    val content: AutoTimerPreviewContent = AutoTimerPreviewContent.Loading,
    val refreshing: Boolean = false,
    val blocked: Boolean = false,
    val pending: Boolean = false,
    val expanded: Set<String> = emptySet(),
    val detail: AutoTimerMatchDetail? = null,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Raw(name)
}

/** A skipped row's key for [AutoTimerPreviewUiState.expanded]. */
val PreviewMatch.key: String
    get() = "$serviceRef@${begin.epochSecond}"

/**
 * What one AutoTimer would record. The route names it by id and name, since the box may have
 * renumbered its AutoTimers; a disabled one offers to enable it instead of a preview.
 */
@HiltViewModel
class AutoTimerPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val autoTimers: AutoTimerRepository,
    private val epg: EpgRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val id = AutoTimerId(savedStateHandle.get<Int>(AutoTimerPreview::id.name) ?: -1)

    private val _uiState = MutableStateFlow(
        AutoTimerPreviewUiState(
            name = savedStateHandle.get<String>(AutoTimerPreview::name.name).orEmpty(),
            blocked = sessions.status.value.blocksMutations
        )
    )
    val uiState: StateFlow<AutoTimerPreviewUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            autoTimers.profileId.drop(1).collect {
                loadJob?.cancel()
                detailJob?.cancel()
                _uiState.update {
                    it.copy(
                        autoTimer = null,
                        content = AutoTimerPreviewContent.Gone,
                        refreshing = false,
                        pending = false,
                        detail = null
                    )
                }
            }
        }
        viewModelScope.launch {
            sessions.status.map { it.blocksMutations }.distinctUntilChanged().collect { blocked ->
                _uiState.update { it.copy(blocked = blocked) }
                if (!blocked && _uiState.value.content is AutoTimerPreviewContent.Failed) {
                    reload()
                }
            }
        }
        reload()
    }

    fun reload() {
        val state = _uiState.value
        if (state.pending || state.content == AutoTimerPreviewContent.Gone) {
            return
        }
        loadJob?.cancel()
        _uiState.update {
            if (it.content is AutoTimerPreviewContent.Ready) {
                it.copy(refreshing = true)
            } else {
                it.copy(content = AutoTimerPreviewContent.Loading)
            }
        }
        loadJob = viewModelScope.launch { show(autoTimers.preview(id, state.name)) }
    }

    /** Enables the disabled AutoTimer, then previews it. */
    fun enable() {
        val state = _uiState.value
        val autoTimer = state.autoTimer
        if (autoTimer == null || state.content != AutoTimerPreviewContent.Disabled ||
            state.blocked || state.pending
        ) {
            return
        }
        _uiState.update { it.copy(pending = true) }
        loadJob = viewModelScope.launch {
            val message = when (val result = autoTimers.setEnabled(autoTimer, true)) {
                is AutoTimerWriteResult.Done -> null
                AutoTimerWriteResult.Conflict -> UiText.Resource(R.string.autotimer_changed)
                is AutoTimerWriteResult.Failed -> result.message
            }
            val load = autoTimers.preview(id, _uiState.value.name)
            _uiState.update { it.copy(pending = false, userMessage = message ?: it.userMessage) }
            show(load)
        }
    }

    fun toggleLog(match: PreviewMatch) {
        _uiState.update {
            val key = match.key
            it.copy(expanded = if (key in it.expanded) it.expanded - key else it.expanded + key)
        }
    }

    /** Opens the EPG sheet of [match] and looks up its programme. */
    fun openMatch(match: PreviewMatch) {
        detailJob?.cancel()
        _uiState.update { it.copy(detail = AutoTimerMatchDetail(match)) }
        detailJob = viewModelScope.launch {
            val event = epg.event(match.serviceRef, match.begin.epochSecond)
            val loaded =
                AutoTimerMatchDetail(match, event?.let(MatchEpg::Found) ?: MatchEpg.Missing)
            _uiState.update { if (it.detail?.match == match) it.copy(detail = loaded) else it }
        }
    }

    fun dismissMatch() {
        detailJob?.cancel()
        _uiState.update { it.copy(detail = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun show(load: AutoTimerPreviewLoad) {
        val (autoTimer, content) = when (load) {
            is AutoTimerPreviewLoad.Ready -> load.autoTimer to AutoTimerPreviewContent.Ready(
                upcoming = load.matches.filter { it.verdict != Verdict.Skip }
                    .sortedBy { it.begin },
                skipped = load.matches.filter { it.verdict == Verdict.Skip }
                    .sortedBy { it.begin }
            )

            is AutoTimerPreviewLoad.Disabled -> load.autoTimer to AutoTimerPreviewContent.Disabled

            AutoTimerPreviewLoad.Gone -> null to AutoTimerPreviewContent.Gone

            AutoTimerPreviewLoad.PluginMissing -> null to AutoTimerPreviewContent.PluginMissing

            is AutoTimerPreviewLoad.PluginFailed ->
                load.autoTimer to AutoTimerPreviewContent.PluginFailed(load.message)

            is AutoTimerPreviewLoad.Failed ->
                _uiState.value.autoTimer to AutoTimerPreviewContent.Failed(load.message)
        }
        _uiState.update { it.copy(autoTimer = autoTimer, content = content, refreshing = false) }
    }
}
