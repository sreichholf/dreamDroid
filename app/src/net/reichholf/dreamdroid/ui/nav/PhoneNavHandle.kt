package net.reichholf.dreamdroid.ui.nav

import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.StateFlow
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.BouquetMode
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer as SleepTimerKeys
import net.reichholf.dreamdroid.ui.session.ConnectionStatus

/**
 * Phone detail-pane navigation owner. [net.reichholf.dreamdroid.activities.MainActivity]
 * holds a [PhoneNavigator] that implements this; destinations no longer take a Fragment.
 */
interface PhoneNavHandle {
    fun interface ActivityResultListener {
        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    }

    val lifecycleOwner: LifecycleOwner

    var composeActivityResultListener: ActivityResultListener?

    val navUiState: StateFlow<PhoneNavUiState>
    fun connectionStatusFlow(): StateFlow<ConnectionStatus>
    fun requestLeaveConfirm()
    fun clearLeaveConfirm()
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
    fun isOnProfileCheckRoute(): Boolean
    fun navigateToProfileCheck(): Boolean
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
    fun navigateToBouquetContent(
        bouquetRef: String,
        bouquetName: String,
        mode: BouquetMode
    ): Boolean
    fun navigateToBouquetAddServices(bouquetRef: String, mode: BouquetMode): Boolean
    fun navigateToAutoTimerPreview(id: Int, name: String): Boolean
    fun navigateToAutoTimerEdit(route: AutoTimerEdit): Boolean
    fun navigateToAutoTimerTargetPick(): Boolean

    /** Hands the picked targets to the AutoTimer editor below and closes the picker. */
    fun deliverAutoTimerTargets(targets: List<Target>): Boolean

    /** Closes the editor and shows the saved AutoTimer instead of a stale preview. */
    fun showAutoTimerAfterSave(id: Int, name: String): Boolean
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
