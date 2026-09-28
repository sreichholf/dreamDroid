package net.reichholf.dreamdroid.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.enigma.ProfileCheckResult

/**
 * The wizard: its [draft] (saved across process death), the receiver search, and the
 * connection check. [finished] is set once the new profile is saved and active.
 */
data class SetupAssistantUiState(
    val draft: SetupDraft = SetupDraft(),
    val devices: List<SetupReceiver> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val checking: Boolean = false,
    val checkResult: ProfileCheckResult? = null,
    val finished: Boolean = false
)

/**
 * Wizard draft, receiver search, and connection check for [SetupAssistantScreen].
 * Activity-scoped on the phone and TV hosts, so a rotation keeps a running search
 * or check instead of starting it again.
 */
@HiltViewModel
class SetupAssistantViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val checks: ProfileCheckRepository,
    private val discovery: ReceiverDiscovery
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SetupAssistantUiState(draft = readSetupDraft(savedStateHandle))
    )
    val uiState: StateFlow<SetupAssistantUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var checkJob: Job? = null
    private var saveJob: Job? = null

    /** Returns true when the local-network prompt should be shown for this wizard. */
    fun claimNetworkRequest(): Boolean {
        if (_uiState.value.draft.askedForNetwork) {
            return false
        }
        updateDraft { it.copy(askedForNetwork = true) }
        return true
    }

    fun search() {
        val state = _uiState.value
        if (state.searching || state.searched) {
            return
        }
        _uiState.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            val found = discovery.find().map { it.toSetupReceiver() }
            _uiState.update { it.copy(devices = found, searching = false, searched = true) }
        }
    }

    fun check() {
        checkJob?.cancel()
        _uiState.update { it.copy(checking = true, checkResult = null) }
        val profile = _uiState.value.draft.toProfile()
        checkJob = viewModelScope.launch {
            val result = checks.check(profile)
            _uiState.update { it.copy(checking = false, checkResult = result) }
        }
    }

    /** Moves one step back. Returns false on Welcome, where Back leaves the wizard. */
    fun back(): Boolean {
        val previous = when (_uiState.value.draft.step) {
            SetupStep.Welcome -> return false
            SetupStep.Find -> SetupStep.Welcome
            SetupStep.Connection -> SetupStep.Find
            SetupStep.SignIn -> SetupStep.Connection
            SetupStep.Name -> SetupStep.SignIn
        }
        updateDraft { it.copy(step = previous) }
        return true
    }

    /** The primary action. On the Name step it saves the profile and makes it active. */
    fun advance() {
        val state = _uiState.value
        when (state.draft.step) {
            SetupStep.Welcome -> updateDraft { it.copy(step = SetupStep.Find) }

            SetupStep.Find -> updateDraft { it.copy(step = SetupStep.Connection) }

            SetupStep.Connection -> updateDraft { it.copy(step = SetupStep.SignIn) }

            SetupStep.SignIn -> {
                if (state.checkResult == null || state.checking) {
                    check()
                } else {
                    updateDraft {
                        val name = if (it.nameEdited) {
                            it.profileName
                        } else {
                            it.suggestedName.ifBlank { it.host.trim() }
                        }
                        it.copy(step = SetupStep.Name, profileName = name)
                    }
                }
            }

            SetupStep.Name -> save()
        }
    }

    fun onFinishHandled() {
        _uiState.update { it.copy(finished = false) }
    }

    fun onHostChange(host: String) {
        updateDraft { it.copy(host = host) }
        clearCheckResult()
    }

    fun onFindHostChange(host: String) {
        updateDraft { it.copy(host = host, suggestedName = "") }
        clearCheckResult()
    }

    fun onPick(receiver: SetupReceiver) {
        updateDraft {
            it.copy(
                host = receiver.host,
                portText = receiver.port.toString(),
                useHttps = receiver.port == 443,
                suggestedName = receiver.name,
                nameEdited = false
            )
        }
        clearCheckResult()
    }

    fun onHttpsChange(https: Boolean) {
        updateDraft {
            val current = it.portText.toIntOrNull()
            val wasDefault = current == null || current == 80 || current == 443
            val port = when {
                !wasDefault -> it.portText
                https -> "443"
                else -> "80"
            }
            it.copy(useHttps = https, portText = port)
        }
        clearCheckResult()
    }

    fun onPortChange(portText: String) {
        updateDraft { it.copy(portText = portText) }
        clearCheckResult()
    }

    fun onLoginChange(login: Boolean) {
        updateDraft { it.copy(login = login) }
        clearCheckResult()
    }

    fun onUserChange(user: String) {
        updateDraft { it.copy(user = user) }
        clearCheckResult()
    }

    fun onPassChange(pass: String) {
        updateDraft { it.copy(pass = pass) }
        clearCheckResult()
    }

    fun onTrustAllChange(enabled: Boolean) {
        updateDraft { it.copy(trustAllCerts = enabled) }
        check()
    }

    fun onNameChange(name: String) {
        updateDraft { it.copy(profileName = name, nameEdited = true) }
    }

    private fun save() {
        if (saveJob?.isActive == true) {
            return
        }
        val profile = _uiState.value.draft.let {
            if (it.profileName.isBlank()) it.copy(profileName = it.host.trim()) else it
        }.toProfile()
        saveJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                profiles.save(profile)
                profiles.setCurrent(profile.id!!, forceEvent = true)
            }
            // The host activity keeps this ViewModel after it swaps to the shell; a
            // later return to setup must start a fresh wizard.
            reset()
            _uiState.update { it.copy(finished = true) }
        }
    }

    private fun clearCheckResult() {
        checkJob?.cancel()
        checkJob = null
        _uiState.update { it.copy(checkResult = null, checking = false) }
    }

    private fun reset() {
        searchJob?.cancel()
        searchJob = null
        checkJob?.cancel()
        checkJob = null
        val fresh = SetupDraft()
        fresh.writeTo(savedStateHandle)
        _uiState.value = SetupAssistantUiState(draft = fresh)
    }

    private fun updateDraft(transform: (SetupDraft) -> SetupDraft) {
        val next = transform(_uiState.value.draft)
        next.writeTo(savedStateHandle)
        _uiState.update { it.copy(draft = next) }
    }
}
