package net.reichholf.dreamdroid.ui.current

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.loadCurrentService
import net.reichholf.dreamdroid.enigma.simpleResultFromFetch
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState

/**
 * Load, saved snapshot, and timer-add for [CurrentServiceDestination].
 * The EPG sheet open flag stays in the composable.
 */
class CurrentServiceViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val uiState: CurrentServiceUiState = CurrentServiceUiState()

    var refreshing by mutableStateOf(false)
        private set

    var toolbarTitle by mutableStateOf("")
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    var progress by mutableStateOf<IndeterminateProgressState?>(null)
        private set

    var current by mutableStateOf<CurrentService?>(null)
        private set

    var currentItem by mutableStateOf<Event?>(null)
        private set

    var ready by mutableStateOf(false)
        private set

    private val gate = CurrentServiceLoadGate()
    private val savedAccess = HandleCurrentServiceSavedAccess(savedStateHandle)
    private var saved = CurrentServiceSaved()
    private var activeProfileId: Int = -1
    private var started = false
    private var loadJob: Job? = null
    private var setTimerJob: Job? = null
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DreamDroid.CURRENT_PROFILE) {
            onCurrentProfileChanged()
        }
    }

    init {
        activeProfileId = currentProfileId()
        saved = adoptSaved(activeProfileId)
        paint(saved)
        toolbarTitle = baseTitle()
        PreferenceManager.getDefaultSharedPreferences(application)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun start() {
        if (started) {
            return
        }
        started = true
        if (loadJob?.isActive == true) {
            return
        }
        val profileId = currentProfileId()
        activeProfileId = profileId
        saved = adoptSaved(profileId)
        paint(saved)
        val shown = gate.visible(profileId)
        if (shown != null) {
            current = shown
            ready = true
            uiState.apply(shown)
            persist(profileId)
            toolbarTitle = baseTitle()
            return
        }
        val restored = current
        if (restored == null || restored.isEmpty()) {
            ready = false
            uiState.clear()
            reload()
        } else {
            ready = true
            uiState.apply(restored)
            persist(profileId)
            toolbarTitle = baseTitle()
        }
    }

    fun reload() {
        val app = getApplication<Application>()
        refreshing = true
        toolbarTitle = app.getString(R.string.loading)
        val generation = gate.beginLoad()
        val loadProfileId = currentProfileId()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = loadCurrentService(app)
            if (!gate.isCurrent(generation)) {
                return@launch
            }
            refreshing = false
            toolbarTitle = baseTitle()
            applyCurrent(
                generation,
                loadProfileId,
                if (result.success) result.current else null
            )
        }
    }

    fun consumeError() {
        errorText = null
    }

    fun rememberCurrentItem(event: Event) {
        currentItem = event
        persist()
    }

    fun addTimer() {
        if (progress != null) {
            return
        }
        val event = currentItem ?: return
        val app = getApplication<Application>()
        progress = IndeterminateProgressState(message = app.getString(R.string.saving))
        setTimerJob?.cancel()
        setTimerJob = viewModelScope.launch {
            val handler = TimerAddByEventIdRequestHandler()
            val params = Timer.getEventIdParams(event)
            val http = EnigmaHttp()
            val fetched = withContext(Dispatchers.IO) {
                simpleResultFromFetch(handler.fetch(http, params)) { xml ->
                    handler.parseSimpleResult(xml)
                }
            }
            if (!isActive) {
                return@launch
            }
            progress = null
            errorText = timerResultMessage(fetched.second, fetched.third)
        }
    }

    override fun onCleared() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    private fun onCurrentProfileChanged() {
        val nextId = currentProfileId()
        gate.beginLoad()
        if (!started) {
            activeProfileId = nextId
            return
        }
        if (nextId == activeProfileId) {
            return
        }
        activeProfileId = nextId
        loadJob?.cancel()
        // rememberSaveable(profileId) dropped the other profile's current service.
        current = null
        val shown = gate.visible(nextId)
        if (shown != null) {
            current = shown
            ready = true
            uiState.apply(shown)
            persist(nextId)
            refreshing = false
            toolbarTitle = baseTitle()
            return
        }
        ready = false
        uiState.clear()
        saved = CurrentServiceSaved(
            current = null,
            item = currentItem,
            ready = false,
            profileId = nextId
        )
        saved.writeTo(savedAccess)
        reload()
    }

    private fun adoptSaved(profileId: Int): CurrentServiceSaved {
        val savedProfileId = savedAccess.getProfileId()
        if (!shouldRestoreCurrentService(savedProfileId, profileId)) {
            if (savedProfileId != null) {
                CurrentServiceSaved(profileId = profileId).writeTo(savedAccess)
            }
            return CurrentServiceSaved()
        }
        return readCurrentServiceSaved(savedAccess, profileId)
    }

    private fun paint(snapshot: CurrentServiceSaved) {
        current = snapshot.current
        currentItem = snapshot.item
        val shown = current
        if (shown != null && !shown.isEmpty()) {
            ready = true
            uiState.apply(shown)
        } else {
            ready = snapshot.ready
        }
    }

    private fun applyCurrent(generation: Int, loadProfileId: Int, content: CurrentService?) {
        if (content != null && !content.isEmpty()) {
            if (!gate.applySuccess(generation, loadProfileId, content)) {
                return
            }
        } else if (!gate.isCurrent(generation)) {
            return
        }
        publishVisible(generation)
    }

    private fun publishVisible(generation: Int) {
        if (!gate.isCurrent(generation)) {
            return
        }
        val profileId = currentProfileId()
        val shown = gate.visible(profileId)
        if (shown != null) {
            current = shown
            ready = true
            uiState.apply(shown)
            persist(profileId)
            return
        }
        // No last-good: the screen shows empty-but-ready. The saved ready flag stays
        // false when this was the first load, so a later restore fetches again.
        uiState.apply(null)
    }

    private fun persist(profileId: Int = currentProfileId()) {
        saved = CurrentServiceSaved(
            current = current,
            item = currentItem,
            ready = ready,
            profileId = profileId
        )
        saved.writeTo(savedAccess)
    }

    private fun timerResultMessage(result: SimpleResult, error: EnigmaHttpError?): String {
        val app = getApplication<Application>()
        val stateText = result.stateText
        if (!stateText.isNullOrEmpty()) {
            return stateText
        }
        if (error != null) {
            return error.resolve(app).orEmpty()
        }
        return app.getString(R.string.get_content_error)
    }

    private fun currentProfileId(): Int = ProfileRepository.get().requireCurrent().id ?: -1

    private fun baseTitle(): String =
        getApplication<Application>().getString(R.string.current_service)
}

private class HandleCurrentServiceSavedAccess(private val handle: SavedStateHandle) :
    CurrentServiceSavedAccess {
    override fun getCurrent(): CurrentService? =
        handle.get<CurrentService>(CurrentServiceSavedKeys.CURRENT)

    override fun setCurrent(current: CurrentService?) {
        if (current == null) {
            handle.remove<CurrentService>(CurrentServiceSavedKeys.CURRENT)
        } else {
            handle[CurrentServiceSavedKeys.CURRENT] = current
        }
    }

    override fun getItem(): Event? = handle.get<Event>(CurrentServiceSavedKeys.ITEM)

    override fun setItem(item: Event?) {
        if (item == null) {
            handle.remove<Event>(CurrentServiceSavedKeys.ITEM)
        } else {
            handle[CurrentServiceSavedKeys.ITEM] = item
        }
    }

    override fun getReady(): Boolean = handle.get<Boolean>(CurrentServiceSavedKeys.READY) ?: false

    override fun setReady(ready: Boolean) {
        handle[CurrentServiceSavedKeys.READY] = ready
    }

    override fun getProfileId(): Int? = handle.get<Int>(CurrentServiceSavedKeys.PROFILE_ID)

    override fun setProfileId(profileId: Int?) {
        if (profileId == null) {
            handle.remove<Int>(CurrentServiceSavedKeys.PROFILE_ID)
        } else {
            handle[CurrentServiceSavedKeys.PROFILE_ID] = profileId
        }
    }
}
