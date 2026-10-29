package net.reichholf.dreamdroid.ui.setup

import androidx.annotation.StringRes
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
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BackupDocuments
import net.reichholf.dreamdroid.data.BackupRepository
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.ui.backup.BackupImport
import net.reichholf.dreamdroid.ui.backup.ImportReview
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The wizard: its [draft] (saved across process death), the receiver search, the connection
 * check, and the restore of a picked backup. The typed fields are on
 * [SetupAssistantViewModel]. [finished] is set once a profile is saved or restored and active.
 */
data class SetupAssistantUiState(
    val draft: SetupDraft = SetupDraft(),
    val devices: List<SetupReceiver> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val checking: Boolean = false,
    val checkResult: ProfileCheckResult? = null,
    val restoring: Boolean = false,
    val restoreError: UiText? = null,
    val finished: Boolean = false
)

/**
 * Wizard draft, receiver search, connection check, and backup restore for
 * [SetupAssistantScreen]. Activity-scoped on the phone and TV hosts, so a rotation keeps a
 * running search or check instead of starting it again.
 */
@HiltViewModel
class SetupAssistantViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val checks: ProfileCheckRepository,
    private val discovery: ReceiverDiscovery,
    backups: BackupRepository,
    documents: BackupDocuments
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SetupAssistantUiState(draft = readSetupDraft(savedStateHandle))
    )
    val uiState: StateFlow<SetupAssistantUiState> = _uiState.asStateFlow()

    private val imports = BackupImport(savedStateHandle, backups, profiles, documents)

    /** The backup the user picked on Welcome, while its review step is open. */
    val importReview: StateFlow<ImportReview?> = imports.review

    /** Typed on Find and Connection. On Find, typing drops the picked receiver's name. */
    val host = textField(SetupAssistantSavedKeys.HOST, "") {
        if (_uiState.value.draft.step == SetupStep.Find) {
            updateDraft { it.copy(suggestedName = "") }
        }
        clearCheckResult()
    }
    val port = textField(SetupAssistantSavedKeys.PORT_TEXT, SetupDefaults.PORT_TEXT) {
        clearCheckResult()
    }
    val user = textField(SetupAssistantSavedKeys.USER, SetupDefaults.USER) { clearCheckResult() }
    val pass = textField(SetupAssistantSavedKeys.PASS, SetupDefaults.PASS) { clearCheckResult() }
    val profileName = textField(SetupAssistantSavedKeys.PROFILE_NAME, "") {
        updateDraft { it.copy(nameEdited = true) }
    }

    private var searchJob: Job? = null
    private var checkJob: Job? = null
    private var saveJob: Job? = null
    private var restoreJob: Job? = null

    init {
        viewModelScope.launch {
            val review = imports.reopenSaved()
            if (!review && _uiState.value.draft.step == SetupStep.Import) {
                updateDraft { it.copy(step = SetupStep.Welcome) }
            }
        }
    }

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
        val profile = toProfile(profileName.text)
        checkJob = viewModelScope.launch {
            val result = checks.check(profile)
            _uiState.update { it.copy(checking = false, checkResult = result) }
        }
    }

    /** Moves one step back. Returns false on Welcome, where Back leaves the wizard. */
    fun back(): Boolean {
        val current = _uiState.value.draft.step
        // A running restore cannot be abandoned: its profile write is not cancellable.
        if (current == SetupStep.Import && _uiState.value.restoring) {
            return true
        }
        val previous = when (current) {
            SetupStep.Welcome -> return false
            SetupStep.Find -> SetupStep.Welcome
            SetupStep.Connection -> SetupStep.Find
            SetupStep.SignIn -> SetupStep.Connection
            SetupStep.Name -> SetupStep.SignIn
            SetupStep.Import -> SetupStep.Welcome
        }
        if (current == SetupStep.Import) {
            imports.clear()
        }
        updateDraft { it.copy(step = previous) }
        return true
    }

    /** The primary action. On Name it saves the profile; on Import it restores the backup. */
    fun advance() {
        val state = _uiState.value
        when (state.draft.step) {
            SetupStep.Welcome -> {
                _uiState.update { it.copy(restoreError = null) }
                updateDraft { it.copy(step = SetupStep.Find) }
            }

            SetupStep.Find -> updateDraft { it.copy(step = SetupStep.Connection) }

            SetupStep.Connection -> updateDraft { it.copy(step = SetupStep.SignIn) }

            SetupStep.SignIn -> {
                if (state.checking) {
                    // The button stays enabled during a check so it keeps D-pad focus.
                    return
                } else if (state.checkResult == null) {
                    check()
                } else {
                    if (!state.draft.nameEdited) {
                        profileName.set(state.draft.suggestedName.ifBlank { host.text.trim() })
                    }
                    updateDraft { it.copy(step = SetupStep.Name) }
                }
            }

            SetupStep.Name -> save()

            SetupStep.Import -> restore()
        }
    }

    fun onFinishHandled() {
        _uiState.update { it.copy(finished = false) }
    }

    fun onPick(receiver: SetupReceiver) {
        host.set(receiver.host)
        port.set(receiver.port.toString())
        updateDraft {
            it.copy(
                useHttps = receiver.port == 443,
                suggestedName = receiver.name,
                nameEdited = false
            )
        }
        clearCheckResult()
    }

    fun onHttpsChange(https: Boolean) {
        val current = port.text.toIntOrNull()
        val wasDefault = current == null || current == 80 || current == 443
        if (wasDefault) {
            port.set(if (https) "443" else "80")
        }
        updateDraft { it.copy(useHttps = https) }
        clearCheckResult()
    }

    fun onLoginChange(login: Boolean) {
        updateDraft { it.copy(login = login) }
        clearCheckResult()
    }

    fun onTrustAllChange(enabled: Boolean) {
        updateDraft { it.copy(trustAllCerts = enabled) }
        check()
    }

    /**
     * Reads the backup the user picked. An unreadable file, or one without profiles, stays
     * on Welcome with [SetupAssistantUiState.restoreError]; a usable one opens its review.
     */
    fun restoreFrom(uri: String) {
        if (_uiState.value.restoring) {
            return
        }
        _uiState.update { it.copy(restoring = true, restoreError = null) }
        restoreJob = viewModelScope.launch {
            val opened = imports.open(uri)
            val hasProfiles = imports.review.value?.profiles?.isNotEmpty() == true
            if (!opened || !hasProfiles) {
                imports.clear()
                _uiState.update {
                    it.copy(
                        restoring = false,
                        restoreError = UiText.Resource(
                            if (opened) {
                                R.string.setup_restore_no_profiles
                            } else {
                                R.string.setup_restore_failed
                            }
                        )
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(restoring = false) }
            updateDraft { it.copy(step = SetupStep.Import) }
        }
    }

    fun onRestorePickerMissing() {
        _uiState.update {
            it.copy(restoreError = UiText.Resource(R.string.no_file_picker))
        }
    }

    fun setImportProfileChecked(index: Int, checked: Boolean) =
        imports.setProfileChecked(index, checked)

    fun setAllImportProfilesChecked(checked: Boolean) = imports.setAllProfilesChecked(checked)

    fun setImportPasswords(include: Boolean) = imports.setPasswords(include)

    fun setImportSettings(include: Boolean) = imports.setSettings(include)

    private fun restore() {
        if (restoreJob?.isActive == true) {
            return
        }
        val review = imports.review.value ?: return
        // Settings alone write no profile, and the shell needs an active one.
        if (review.selectedProfiles == 0) {
            return
        }
        _uiState.update { it.copy(restoring = true, restoreError = null) }
        restoreJob = viewModelScope.launch {
            if (!imports.confirm()) {
                restoreFailed(R.string.setup_restore_failed)
                return@launch
            }
            // confirm() wrote at least one profile (restore() refuses without one), so this
            // only fails if the store dropped the row.
            if (!profiles.ensureCurrent()) {
                restoreFailed(R.string.setup_restore_failed)
                return@launch
            }
            reset()
            _uiState.update { it.copy(finished = true) }
        }
    }

    /** Gives up the restore step: back on Welcome, with [message] under the buttons. */
    private fun restoreFailed(@StringRes message: Int) {
        updateDraft { it.copy(step = SetupStep.Welcome) }
        _uiState.update {
            it.copy(restoring = false, restoreError = UiText.Resource(message))
        }
    }

    private fun save() {
        if (saveJob?.isActive == true) {
            return
        }
        val profile = toProfile(profileName.text.ifBlank { host.text.trim() })
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

    private fun toProfile(name: String): Profile {
        val draft = _uiState.value.draft
        val profile = wizardProfile(
            name = name,
            host = host.text,
            port = setupPort(port.text, draft.useHttps),
            useHttps = draft.useHttps,
            login = draft.login,
            user = user.text,
            pass = pass.text,
            trustAllCerts = draft.trustAllCerts
        )
        profiles.applyDefaultPiconSettings(profile)
        return profile
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
        imports.clear()
        host.set("")
        port.set(SetupDefaults.PORT_TEXT)
        user.set(SetupDefaults.USER)
        pass.set(SetupDefaults.PASS)
        profileName.set("")
        val fresh = SetupDraft()
        fresh.writeTo(savedStateHandle)
        _uiState.value = SetupAssistantUiState(draft = fresh)
    }

    private fun updateDraft(transform: (SetupDraft) -> SetupDraft) {
        val next = transform(_uiState.value.draft)
        next.writeTo(savedStateHandle)
        _uiState.update { it.copy(draft = next) }
    }

    private fun textField(key: String, initial: String, onEdit: () -> Unit) =
        SavedTextField(viewModelScope, savedStateHandle, key, initial) { onEdit() }
}
