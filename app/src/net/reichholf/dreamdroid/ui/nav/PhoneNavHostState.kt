package net.reichholf.dreamdroid.ui.nav

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.Serializable
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.StreamMode
import net.reichholf.dreamdroid.enigma.Timer

internal const val PHONE_NAV_SCHEMA_KEY = "dreamdroid_nav_schema"
internal const val PHONE_NAV_SCHEMA_VERSION = 2

/**
 * What the phone NavHost shows beside its destinations: the EPG and EPG search remount
 * epochs, which rebuild those destinations for new arguments, and the leave-confirm and
 * needs-receiver dialogs.
 */
data class PhoneNavUiState(
    val epgRemount: Int = 0,
    val epgSearchRemount: Int = 0,
    val leaveConfirmRequested: Boolean = false,
    val needsReceiverRequested: Boolean = false
)

/** An activity result held until a destination listens for results again. */
data class PendingComposeActivityResult(val requestCode: Int, val resultCode: Int)

/**
 * Navigations asked for while no NavHostController was attached. [PhoneNavigator] runs
 * them on the next attach, in the order of these fields.
 */
internal class PendingNavigations {
    var drawerRoot: Any? = null
    var profileEditRequested: Boolean = false
    var profileEdit: Profile? = null
    var timerEdit: Timer? = null
    var timerCreate: Boolean = false
    var epgSearchQuery: String? = null
    var backup: Boolean = false
    var about: Boolean = false
    var power: Boolean = false
    var sendMessage: Boolean = false
    var sleepTimer: SleepTimerRoute? = null
    var openSleepTimer: Boolean = false
    var changelog: Boolean = false
    var profileCheck: Boolean = false
    var multiEpg: MultiEpg? = null
}

/**
 * The profile the EPG is mounted for, as it goes into saved state: the id and the
 * connection settings [Profile.hasSameSettings] compares, without the passwords. Saved
 * state outlives the process and must not carry a receiver password.
 */
private data class MountedEpgProfile(
    val id: Int?,
    val host: String?,
    val streamHostOrHost: String?,
    val user: String?,
    val login: Boolean,
    val ssl: Boolean,
    val simpleRemote: Boolean,
    val port: Int,
    val streamPort: Int,
    val filePort: Int,
    val streamLogin: Boolean,
    val streamSsl: Boolean,
    val fileSsl: Boolean,
    val fileLogin: Boolean,
    val zapAndStream: Boolean,
    val streamMode: StreamMode,
    val transcodePort: Int,
    val encoderPort: Int,
    val encoderPath: String?,
    val encoderLogin: Boolean,
    val encoderUser: String?,
    val encoderVideoBitrate: Int,
    val encoderAudioBitrate: Int
) : Serializable {
    companion object {
        fun from(profile: Profile): MountedEpgProfile = MountedEpgProfile(
            id = profile.id,
            host = profile.host,
            streamHostOrHost = profile.streamHostOrHost,
            user = profile.user,
            login = profile.login,
            ssl = profile.ssl,
            simpleRemote = profile.simpleRemote,
            port = profile.port,
            streamPort = profile.streamPort,
            filePort = profile.filePort,
            streamLogin = profile.streamLogin,
            streamSsl = profile.streamSsl,
            fileSsl = profile.fileSsl,
            fileLogin = profile.fileLogin,
            zapAndStream = profile.zapAndStream,
            streamMode = profile.streamMode,
            transcodePort = profile.transcodePort,
            encoderPort = profile.encoderPort,
            encoderPath = profile.encoderPath,
            encoderLogin = profile.encoderLogin,
            encoderUser = profile.encoderUser,
            encoderVideoBitrate = profile.encoderVideoBitrate,
            encoderAudioBitrate = profile.encoderAudioBitrate
        )
    }
}

/**
 * The phone navigation state that outlives the activity: the saved start route, the
 * request codes of open result destinations, the route schema flag, the queued
 * navigations, and an activity result held until a destination listens. The activity's
 * [PhoneNavigator] owns the NavHostController and the other UI objects and keeps its state
 * here.
 */
@HiltViewModel
class PhoneNavHostState @Inject constructor(private val savedStateHandle: SavedStateHandle) :
    ViewModel() {
    private val _uiState = MutableStateFlow(
        PhoneNavUiState(epgRemount = savedStateHandle[PhoneNavSavedKeys.EPG_REMOUNT] ?: 0)
    )
    val uiState: StateFlow<PhoneNavUiState> = _uiState.asStateFlow()

    internal val pending = PendingNavigations()

    private val resultRequestCodes: ArrayDeque<Int> = ArrayDeque()
    private var startRouteValue: String = PhoneNavRoutes.DEVICE_INFO
    private var startRouteSaved: Boolean = false
    private var heldResult: PendingComposeActivityResult? = null
    private var heldResultData: Intent? = null
    private var mountedEpgProfile: MountedEpgProfile? =
        savedStateHandle[PhoneNavSavedKeys.EPG_PROFILE]

    init {
        val bag = readPhoneNavStateBag(savedStateHandle)
        startRouteSaved = bag.hasSavedStartRoute()
        startRouteValue = bag.startRoute ?: PhoneNavRoutes.DEVICE_INFO
        bag.pickRequestCodes.forEach { resultRequestCodes.addLast(it) }
    }

    val startRoute: String
        get() = startRouteValue

    fun hasSavedStartRoute(): Boolean = startRouteSaved

    fun setStartRoute(route: String) {
        startRouteValue = route
        startRouteSaved = true
        persistPlain()
    }

    /** False until this process has accepted the type-safe route schema. */
    fun hasNavSchema(): Boolean =
        savedStateHandle.get<Int>(PHONE_NAV_SCHEMA_KEY) == PHONE_NAV_SCHEMA_VERSION

    fun markNavSchema() {
        savedStateHandle[PHONE_NAV_SCHEMA_KEY] = PHONE_NAV_SCHEMA_VERSION
    }

    fun pushResultRequestCode(code: Int) {
        resultRequestCodes.addLast(code)
        persistPlain()
    }

    /** The request code of the newest open result destination; null when there is none. */
    fun popResultRequestCode(): Int? {
        val code = resultRequestCodes.pollLast() ?: return null
        persistPlain()
        return code
    }

    fun clearResultRequestCodes() {
        if (resultRequestCodes.isEmpty()) {
            return
        }
        resultRequestCodes.clear()
        persistPlain()
    }

    /** Keeps a result that arrived while no destination listened; replaces an older one. */
    fun holdActivityResult(result: PendingComposeActivityResult, data: Intent?) {
        heldResult = result
        heldResultData = data
    }

    /** The held result and its data, now released; null when none is held. */
    fun takeHeldActivityResult(): Pair<PendingComposeActivityResult, Intent?>? {
        val result = heldResult ?: return null
        val data = heldResultData
        heldResult = null
        heldResultData = null
        return result to data
    }

    fun remountEpg() {
        val remount = _uiState.updateAndGet { it.copy(epgRemount = it.epgRemount + 1) }.epgRemount
        savedStateHandle[PhoneNavSavedKeys.EPG_REMOUNT] = remount
    }

    /**
     * Remounts the EPG and MultiEPG only when [profile]'s id or connection settings differ
     * from the ones they are mounted for; the passwords do not, so they stay out of saved
     * state. Nothing recorded yet means the EPG is not mounted, or is mounting for
     * [profile] right now, so the first check after a start only records the profile and
     * leaves time, bouquet and scroll alone. A check without an active profile cannot tell
     * what the EPG shows and changes nothing. The record is saved, so a process Android
     * restores does not remount for the profile the EPG already shows.
     */
    fun remountEpgForProfile(profile: Profile?) {
        if (profile == null) {
            return
        }
        val record = MountedEpgProfile.from(profile)
        val previous = mountedEpgProfile
        mountedEpgProfile = record
        savedStateHandle[PhoneNavSavedKeys.EPG_PROFILE] = record
        if (previous != null && previous != record) {
            remountEpg()
        }
    }

    fun remountEpgSearch() {
        _uiState.update { it.copy(epgSearchRemount = it.epgSearchRemount + 1) }
    }

    fun requestLeaveConfirm() {
        _uiState.update { it.copy(leaveConfirmRequested = true) }
    }

    fun clearLeaveConfirm() {
        _uiState.update { it.copy(leaveConfirmRequested = false) }
    }

    fun requestNeedsReceiver() {
        _uiState.update { it.copy(needsReceiverRequested = true) }
    }

    fun clearNeedsReceiver() {
        _uiState.update { it.copy(needsReceiverRequested = false) }
    }

    private fun persistPlain() {
        PhoneNavStateBag(
            startRoute = if (startRouteSaved) startRouteValue else null,
            pickRequestCodes = resultRequestCodes.toList()
        ).writePlain(savedStateHandle)
    }
}
