package net.reichholf.dreamdroid.ui.nav

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.PowerStateSetOutcome
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.SleepTimerLoadOutcome
import net.reichholf.dreamdroid.enigma.fetchPowerStateSet
import net.reichholf.dreamdroid.enigma.fetchSleepTimer
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Message
import net.reichholf.dreamdroid.helpers.enigma2.PowerState as PowerStateKeys
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer as SleepTimerKeys
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MessageRequestHandler

/**
 * Power, sleep timer, and send message. Activity-scoped so a configuration
 * change does not cancel the request. Results go to [ShellMessages]. A sleep
 * timer read that should open the dialog is handed to the current activity.
 */
class ShellViewModel(
    application: Application,
    private val setPower: suspend (String, Context) -> PowerStateSetOutcome,
    private val loadSleep: suspend (List<NameValuePair>, Context) -> SleepTimerLoadOutcome
) : AndroidViewModel(application) {
    private var powerJob: Job? = null
    private var sleepJob: Job? = null
    private var messageJob: Job? = null

    private var sleepTimerOpenerOwner: Any? = null
    private var sleepTimerOpener: ((SleepTimer) -> Unit)? = null
    private var pendingSleepTimer: SleepTimer? = null

    fun onPowerMenuAction(itemId: Int) {
        val state = when (itemId) {
            Statics.ITEM_TOGGLE_STANDBY -> PowerStateKeys.STATE_TOGGLE
            Statics.ITEM_RESTART_GUI -> PowerStateKeys.STATE_GUI_RESTART
            Statics.ITEM_REBOOT -> PowerStateKeys.STATE_SYSTEM_REBOOT
            Statics.ITEM_SHUTDOWN -> PowerStateKeys.STATE_SHUTDOWN
            else -> return
        }
        setPowerState(state)
    }

    fun setPowerState(state: String) {
        powerJob?.cancel()
        powerJob = viewModelScope.launch {
            val outcome = setPower(state, getApplication())
            publishPower(outcome)
        }
    }

    fun loadSleepTimerForDialog() {
        sleepJob?.cancel()
        sleepJob = viewModelScope.launch {
            val outcome = loadSleep(emptyList(), getApplication())
            publishSleep(outcome, openDialog = true)
        }
    }

    fun setSleepTimer(time: String?, action: String?, enabled: Boolean) {
        val params = listOf(
            NameValuePair("cmd", SleepTimerKeys.CMD_SET),
            NameValuePair("time", time),
            NameValuePair("action", action),
            NameValuePair("enabled", if (enabled) Python.TRUE else Python.FALSE)
        )
        sleepJob?.cancel()
        sleepJob = viewModelScope.launch {
            val outcome = loadSleep(params, getApplication())
            publishSleep(outcome, openDialog = false)
        }
    }

    fun sendMessage(text: String?, type: String?, timeout: String?) {
        messageJob?.cancel()
        messageJob = viewModelScope.launchSimpleResultLoad(
            MessageRequestHandler(),
            Message.getParams(text, type, timeout)
        ) { _, result, error ->
            messageJob = null
            val app = getApplication<Application>()
            ShellMessages.post(
                mutationResultText(
                    stateText = result.stateText,
                    errorText = error?.resolve(app),
                    fallback = app.getString(R.string.get_content_error)
                )
            )
        }
    }

    /**
     * The current activity receives a fetched sleep timer. A result that arrives
     * while no activity is bound waits until the next [bindSleepTimerOpener].
     * [unbindSleepTimerOpener] ignores a call from an activity that is no longer
     * the owner, so the previous activity's destroy cannot clear the new one.
     */
    fun bindSleepTimerOpener(owner: Any, opener: (SleepTimer) -> Unit) {
        sleepTimerOpenerOwner = owner
        sleepTimerOpener = opener
        val pending = pendingSleepTimer ?: return
        pendingSleepTimer = null
        opener(pending)
    }

    fun unbindSleepTimerOpener(owner: Any) {
        if (sleepTimerOpenerOwner !== owner) {
            return
        }
        sleepTimerOpenerOwner = null
        sleepTimerOpener = null
    }

    private fun publishPower(outcome: PowerStateSetOutcome) {
        val app = getApplication<Application>()
        val text = when {
            !outcome.success -> outcome.errorText
            outcome.powerState.isRunning == true -> app.getString(R.string.is_running)
            else -> app.getString(R.string.in_standby)
        }
        ShellMessages.post(text)
    }

    private fun publishSleep(outcome: SleepTimerLoadOutcome, openDialog: Boolean) {
        if (outcome.success) {
            if (openDialog) {
                deliverSleepTimer(outcome.timer)
            } else {
                ShellMessages.post(outcome.timer.text)
            }
            return
        }
        ShellMessages.post(getApplication<Application>().getString(R.string.error))
    }

    private fun deliverSleepTimer(timer: SleepTimer) {
        val opener = sleepTimerOpener
        if (opener != null) {
            opener(timer)
        } else {
            pendingSleepTimer = timer
        }
    }

    companion object {
        fun factory(
            setPower: suspend (String, Context) -> PowerStateSetOutcome = ::fetchPowerStateSet,
            loadSleep: suspend (List<NameValuePair>, Context) -> SleepTimerLoadOutcome =
                ::fetchSleepTimer
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                )
                ShellViewModel(app, setPower, loadSleep)
            }
        }

        val Factory: ViewModelProvider.Factory = factory()
    }
}
