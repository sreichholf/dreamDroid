package net.reichholf.dreamdroid.ui.setup

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector

interface SetupAssistantBackend {
    suspend fun search(): List<SetupReceiver>
    suspend fun check(profile: Profile): ProfileCheckResult
}

private class DeviceSetupBackend(private val context: Context) : SetupAssistantBackend {
    override suspend fun search(): List<SetupReceiver> = withContext(Dispatchers.IO) {
        DeviceDetector.getAvailableHosts().map { it.toSetupReceiver() }
    }

    override suspend fun check(profile: Profile): ProfileCheckResult {
        profile.cachedDeviceInfo = null
        return withContext(Dispatchers.IO) { CheckProfile.checkProfile(profile, context) }
    }
}

/**
 * Wizard draft, receiver search, and connection check for [SetupAssistantScreen].
 * Activity-scoped on the phone and TV hosts, so a rotation keeps a running search
 * or check instead of starting it again.
 */
class SetupAssistantViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val backend: SetupAssistantBackend
) : AndroidViewModel(application) {
    constructor(application: Application, savedStateHandle: SavedStateHandle) :
        this(application, savedStateHandle, DeviceSetupBackend(application))

    private val savedAccess = HandleSetupAssistantSavedAccess(savedStateHandle)

    var draft by mutableStateOf(readSetupDraft(savedAccess))
        private set

    var devices by mutableStateOf<List<SetupReceiver>>(emptyList())
        private set

    var searching by mutableStateOf(false)
        private set

    var searched by mutableStateOf(false)
        private set

    var checking by mutableStateOf(false)
        private set

    var checkResult by mutableStateOf<ProfileCheckResult?>(null)
        private set

    private var searchJob: Job? = null
    private var checkJob: Job? = null

    /** Returns true when the local-network prompt should be shown for this wizard. */
    fun claimNetworkRequest(): Boolean {
        if (draft.askedForNetwork) {
            return false
        }
        update { it.copy(askedForNetwork = true) }
        return true
    }

    fun search() {
        if (searching || searched) {
            return
        }
        searching = true
        searchJob = viewModelScope.launch {
            devices = backend.search()
            searching = false
            searched = true
        }
    }

    fun check() {
        checkJob?.cancel()
        checking = true
        checkResult = null
        val profile = draft.toProfile()
        checkJob = viewModelScope.launch {
            checkResult = backend.check(profile)
            checking = false
        }
    }

    /** Moves one step back. Returns false on Welcome, where Back leaves the wizard. */
    fun back(): Boolean {
        val previous = when (draft.step) {
            SetupStep.Welcome -> return false
            SetupStep.Find -> SetupStep.Welcome
            SetupStep.Connection -> SetupStep.Find
            SetupStep.SignIn -> SetupStep.Connection
            SetupStep.Name -> SetupStep.SignIn
        }
        update { it.copy(step = previous) }
        return true
    }

    /** The primary action. Returns the profile to save once the Name step completes. */
    fun advance(): Profile? {
        when (draft.step) {
            SetupStep.Welcome -> update { it.copy(step = SetupStep.Find) }

            SetupStep.Find -> update { it.copy(step = SetupStep.Connection) }

            SetupStep.Connection -> update { it.copy(step = SetupStep.SignIn) }

            SetupStep.SignIn -> {
                if (checkResult == null || checking) {
                    check()
                } else {
                    update {
                        val name = if (it.nameEdited) {
                            it.profileName
                        } else {
                            it.suggestedName.ifBlank { it.host.trim() }
                        }
                        it.copy(step = SetupStep.Name, profileName = name)
                    }
                }
            }

            SetupStep.Name -> {
                val profile = draft.let {
                    if (it.profileName.isBlank()) it.copy(profileName = it.host.trim()) else it
                }.toProfile()
                // The host activity keeps this ViewModel after setContent swaps to the
                // shell; a later return to setup must start a fresh wizard.
                reset()
                return profile
            }
        }
        return null
    }

    fun onHostChange(host: String) {
        update { it.copy(host = host) }
        clearCheckResult()
    }

    fun onFindHostChange(host: String) {
        update { it.copy(host = host, suggestedName = "") }
        clearCheckResult()
    }

    fun onPick(receiver: SetupReceiver) {
        update {
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
        update {
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
        update { it.copy(portText = portText) }
        clearCheckResult()
    }

    fun onLoginChange(login: Boolean) {
        update { it.copy(login = login) }
        clearCheckResult()
    }

    fun onUserChange(user: String) {
        update { it.copy(user = user) }
        clearCheckResult()
    }

    fun onPassChange(pass: String) {
        update { it.copy(pass = pass) }
        clearCheckResult()
    }

    fun onTrustAllChange(enabled: Boolean) {
        update { it.copy(trustAllCerts = enabled) }
        check()
    }

    fun onNameChange(name: String) {
        update { it.copy(profileName = name, nameEdited = true) }
    }

    private fun clearCheckResult() {
        checkJob?.cancel()
        checkJob = null
        checkResult = null
        checking = false
    }

    private fun reset() {
        searchJob?.cancel()
        searchJob = null
        clearCheckResult()
        devices = emptyList()
        searching = false
        searched = false
        update { SetupDraft() }
    }

    private fun update(transform: (SetupDraft) -> SetupDraft) {
        draft = transform(draft)
        draft.writeTo(savedAccess)
    }
}

private class HandleSetupAssistantSavedAccess(private val handle: SavedStateHandle) :
    SetupAssistantSavedAccess {
    override fun get(key: String): Any? = handle.get<Any>(key)

    override fun set(key: String, value: Any) {
        handle[key] = value
    }
}
