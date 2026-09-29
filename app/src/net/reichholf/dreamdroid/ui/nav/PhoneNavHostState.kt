package net.reichholf.dreamdroid.ui.nav

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.ArrayDeque
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.reichholf.dreamdroid.Profile
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
 * The phone navigation state that outlives the activity: the saved start route, the
 * request codes of open result destinations, the route schema flag, the queued
 * navigations, and an activity result held until a destination listens. The activity's
 * [PhoneNavigator] owns the NavHostController and the other UI objects and keeps its state
 * here.
 */
@HiltViewModel
class PhoneNavHostState @Inject constructor(private val savedStateHandle: SavedStateHandle) :
    ViewModel() {
    private val _uiState = MutableStateFlow(PhoneNavUiState())
    val uiState: StateFlow<PhoneNavUiState> = _uiState.asStateFlow()

    internal val pending = PendingNavigations()

    private val resultRequestCodes: ArrayDeque<Int> = ArrayDeque()
    private var startRouteValue: String = PhoneNavRoutes.DEVICE_INFO
    private var startRouteSaved: Boolean = false
    private var heldResult: PendingComposeActivityResult? = null
    private var heldResultData: Intent? = null

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
        _uiState.update { it.copy(epgRemount = it.epgRemount + 1) }
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
