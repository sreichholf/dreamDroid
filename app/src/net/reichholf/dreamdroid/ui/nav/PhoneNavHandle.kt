package net.reichholf.dreamdroid.ui.nav

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.launchDetectDevicesLoad
import net.reichholf.dreamdroid.enigma.launchLocationsAndTagsLoad
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadMovieList
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer as SleepTimerKeys
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.ConnectionStatus

/**
 * Phone detail-pane navigation owner. [net.reichholf.dreamdroid.activities.MainActivity]
 * holds a [PhoneNavHostState] that implements this; destinations no longer take a Fragment.
 */
interface PhoneNavHandle {
    fun interface ActivityResultListener {
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    }

    val lifecycleOwner: LifecycleOwner

    var composeDialogActionListener: DialogActionListener?
    var composeActivityResultListener: ActivityResultListener?

    val profileEditRemountEpoch: Int
    val timerEditRemountEpoch: Int

    fun profileEditRemountFlow(): StateFlow<Int>
    fun timerEditRemountFlow(): StateFlow<Int>
    fun epgRemountFlow(): StateFlow<Int>
    fun epgSearchRemountFlow(): StateFlow<Int>
    fun profileCheckUiFlow(): StateFlow<ProfileCheckUi>
    fun connectionStatusFlow(): StateFlow<ConnectionStatus>
    fun leaveConfirmRequestedFlow(): StateFlow<Boolean>
    fun requestLeaveConfirm()
    fun clearLeaveConfirm()
    fun needsReceiverRequestedFlow(): StateFlow<Boolean>
    fun requestNeedsReceiver()
    fun clearNeedsReceiver()

    fun startRoute(): String

    fun attachNavController(controller: NavHostController)
    fun detachNavController(controller: NavHostController)

    fun navigateToRoute(route: Any): Boolean
    fun navigateToBackup(): Boolean
    fun navigateToAbout(): Boolean
    fun navigateToPower(): Boolean
    fun navigateToSendMessage(): Boolean
    fun navigateToSleepTimer(timer: SleepTimer): Boolean
    fun queueSleepTimer(timer: SleepTimer)
    fun navigateToChangelog(): Boolean
    fun queueChangelog()
    fun updateProfileCheckUi(ui: ProfileCheckUi)
    fun isOnProfileCheckRoute(): Boolean
    fun navigateToProfileCheck(ui: ProfileCheckUi): Boolean
    fun queueProfileCheck(ui: ProfileCheckUi)
    fun navigateAboveProfileCheck(route: Any): Boolean
    fun navigateReplacingProfileCheck(route: Any): Boolean
    fun navigateToEpg(
        serviceReference: String?,
        serviceName: String?,
        timeSec: Long? = null
    ): Boolean
    fun navigateToDrawerEpg(): Boolean
    fun navigateToMultiEpg(
        serviceReference: String?,
        serviceName: String?,
        focusedServiceRef: String? = null,
        timeSec: Long? = null
    ): Boolean
    fun navigateToServiceEpg(serviceReference: String?, serviceName: String?): Boolean
    fun navigateToEpgSearch(query: String?): Boolean
    fun navigateToPickBouquet(requestCode: Int): Boolean
    fun queueProfileEdit(profile: Profile?)
    fun queueTimerEdit(timer: Timer, create: Boolean)
    fun queueEpgSearch(query: String)
    fun navigateToProfileEdit(profile: Profile?): Boolean
    fun popNavBackStack(): Boolean
    fun navigateToTimerEdit(timer: Timer, create: Boolean): Boolean
    fun navigateToTimerServicePick(): Boolean
    fun deliverPickResult(resultCode: Int, data: Intent?)
    fun dispatchPendingComposeActivityResult()
    fun onActiveProfileChanged()
    fun onHostActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
}

data class SleepTimerNavArgs(val minutes: Int, val enabled: Boolean, val action: String) {
    companion object {
        fun from(timer: SleepTimer): SleepTimerNavArgs {
            var minutes = 90
            try {
                minutes = Integer.parseInt(timer.minutes ?: "90")
            } catch (_: NumberFormatException) {
            }
            val enabled = Python.TRUE == timer.enabled
            val action = timer.action ?: SleepTimerKeys.ACTION_STANDBY
            return SleepTimerNavArgs(minutes, enabled, action)
        }

        fun defaults(): SleepTimerNavArgs = SleepTimerNavArgs(
            90,
            false,
            SleepTimerKeys.ACTION_STANDBY
        )
    }
}

fun SleepTimer.toSleepTimerRoute(): SleepTimerRoute {
    val args = SleepTimerNavArgs.from(this)
    return SleepTimerRoute(
        minutes = args.minutes,
        enabled = args.enabled,
        action = args.action
    )
}

fun PhoneNavHandle.runOnlineOnly(action: () -> Unit) {
    if (connectionStatusFlow().value.blocksMutations) {
        requestNeedsReceiver()
    } else {
        action()
    }
}

fun PhoneNavHandle.launchSimpleResultLoad(
    requestHandler: SimpleResultRequestHandler,
    params: List<NameValuePair>,
    profile: Profile? = null,
    onResult: (
        success: Boolean,
        result: net.reichholf.dreamdroid.enigma.SimpleResult,
        error: EnigmaHttpError?
    ) -> Unit
): Job = lifecycleOwner.launchSimpleResultLoad(requestHandler, params, profile, onResult)

fun PhoneNavHandle.launchLocationsAndTagsLoad(
    onProgress: (title: String, progress: String) -> Unit,
    onReady: () -> Unit,
    onLocationsResult: ((success: Boolean) -> Unit)? = null
): Job {
    val context = lifecycleOwner as Context
    return lifecycleOwner.launchLocationsAndTagsLoad(
        context,
        onProgress,
        onReady,
        onLocationsResult
    )
}

fun PhoneNavHandle.launchDetectDevicesLoad(onResult: (profiles: ArrayList<Profile>) -> Unit): Job =
    lifecycleOwner.launchDetectDevicesLoad(onResult)

fun PhoneNavHandle.launchMovieListLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, movies: List<Movie>, errorText: String?) -> Unit
): Job {
    val context = lifecycleOwner as Context
    return lifecycleOwner.lifecycleScope.launch {
        val http = EnigmaHttp()
        withContext(Dispatchers.IO) {
            if (ProfileRepository.get().locations().size <= 1) {
                if (!ProfileRepository.get().loadLocations(http)) {
                    android.util.Log.e(DreamDroid.LOG_TAG, "ERROR loading locations")
                }
            }
            if (ProfileRepository.get().tags().size <= 1) {
                if (!ProfileRepository.get().loadTags(http)) {
                    android.util.Log.e(DreamDroid.LOG_TAG, "ERROR loading tags")
                }
            }
        }
        val result = loadMovieList(context, params)
        onResult(result.success, result.movies, result.errorText)
    }
}
