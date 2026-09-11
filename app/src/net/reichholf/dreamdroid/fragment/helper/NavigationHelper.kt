package net.reichholf.dreamdroid.fragment.helper

import android.content.Context
import android.os.Bundle
import android.util.SparseArray
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.enigma.launchPowerStateSetLoad
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.launchSleepTimerLoad
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.dialogs.PowerStateDialog
import net.reichholf.dreamdroid.fragment.dialogs.SendMessageDialog
import net.reichholf.dreamdroid.fragment.dialogs.SleepTimerDialog
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.helpers.enigma2.Message
import net.reichholf.dreamdroid.helpers.enigma2.PowerState
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MessageRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import net.reichholf.dreamdroid.ui.about.AboutComposeDialog
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.bindDrawerScreen
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes

/**
 * Created by Stephan on 25.12.2015.
 */
open class NavigationHelper(
    activity: MainActivity,
    @JvmField protected val mDrawerState: DrawerListState,
) {
    @JvmField
    var mActivity: MainActivity = activity

    @JvmField
    protected var mPowerStateJob: Job? = null

    @JvmField
    protected var mSleepTimerJob: Job? = null

    @JvmField
    protected var mSimpleResultJob: Job? = null

    @JvmField
    protected var mShc: SimpleHttpClient? = null

    @JvmField
    protected var mSelectedItemId: Int = mDrawerState.selectedItemId

    init {
        val drawerCompose = activity.findViewById<ComposeView>(R.id.drawer_compose)
        if (drawerCompose != null) {
            drawerCompose.bindDrawerScreen(mDrawerState) { itemId ->
                onNavigationItemClick(itemId)
            }
        }
    }

    protected fun getHttpClient(): SimpleHttpClient {
        if (mShc == null) {
            mShc = SimpleHttpClient.getInstance()
        }
        return mShc!!
    }

    protected fun getMainActivity(): MainActivity = mActivity

    protected fun clearBackStack() {
        // Pop the backstack completely everytime the user navigates "away"
        // Avoid's "stacking" fragments due to back-button behaviour that feels
        // really mysterious
        val fm = getMainActivity().supportFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    /**
     * Open a migrated phone NavHost leaf. If [PhoneNavHostFragment] is already the
     * detail pane, navigate in-graph; otherwise mount the host with [route] as start.
     */
    protected fun navigatePhoneNavRoot(route: String) {
        val detail = getMainActivity().supportFragmentManager
            .findFragmentById(R.id.detail_view)
        if (detail is PhoneNavHostFragment && detail.navigateToRoute(route)) {
            return
        }
        clearBackStack()
        getMainActivity().showDetails(PhoneNavHostFragment.newInstance(route))
    }

    fun onDestroy() {
        mPowerStateJob?.cancel(null)
        mPowerStateJob = null
        mSleepTimerJob?.cancel(null)
        mSleepTimerJob = null
        mSimpleResultJob?.cancel(null)
        mSimpleResultJob = null
    }

    protected fun getText(resId: Int): CharSequence = mActivity.getText(resId)

    private fun onPowerStateSet(success: Boolean, result: ExtendedHashMap, resultText: String?) {
        if (!success) {
            showToast(resultText)
            return
        }
        val isRunning = result[PowerState.KEY_IN_STANDBY] as Boolean
        if (isRunning) {
            showToast(getString(R.string.is_running))
        } else {
            showToast(getString(R.string.in_standby))
        }
    }

    protected fun getString(resId: Int): String = mActivity.getString(resId)

    fun onProfileChanged() {
        mShc = SimpleHttpClient.getInstance()
    }

    protected fun setSelectedItem(itemId: Int) {
        if (isDialogItem(itemId)) return
        if (itemId == R.id.menu_navigation_profiles) {
            mDrawerState.clearSelection()
            return
        }
        mDrawerState.select(itemId)
        mSelectedItemId = itemId
    }

    fun navigateTo(itemId: Int) {
        onNavigationItemClick(itemId)
    }

    protected fun isDialogItem(itemId: Int): Boolean {
        for (id in sDialogItemIds) {
            if (id == itemId) return true
        }
        return false
    }

    protected fun onNavigationItemClick(itemId: Int): Boolean {
        setSelectedItem(itemId)

        val navRoot = sNavRootRoutes.get(itemId)
        if (navRoot != null) {
            navigatePhoneNavRoot(navRoot)
            getMainActivity().showContent()
            return true
        }

        when (itemId) {
            R.id.menu_navigation_message ->
                getMainActivity().showDialogFragment(SendMessageDialog.newInstance(), "sendmessage_dialog")

            Statics.ITEM_TOGGLE_STANDBY ->
                setPowerState(PowerState.STATE_TOGGLE)

            Statics.ITEM_RESTART_GUI ->
                setPowerState(PowerState.STATE_GUI_RESTART)

            Statics.ITEM_REBOOT ->
                setPowerState(PowerState.STATE_SYSTEM_REBOOT)

            Statics.ITEM_SHUTDOWN ->
                setPowerState(PowerState.STATE_SHUTDOWN)

            R.id.menu_navigation_power ->
                getMainActivity().showDialogFragment(
                    PowerStateDialog.newInstance(),
                    "powerstate_dialog",
                )

            R.id.menu_navigation_about ->
                getMainActivity().showDialogFragment(AboutComposeDialog.newInstance(), "about_dialog")

            Statics.ITEM_CHECK_CONN ->
                getMainActivity().onProfileChanged(DreamDroid.getCurrentProfile())

            R.id.menu_navigation_changelog ->
                getMainActivity().showChangeLog(false)

            R.id.menu_navigation_sleeptimer ->
                getSleepTimer(true)

            R.id.menu_navigation_epg ->
                navigateToEpg()

            R.id.menu_navigation_backup -> {
                val backupHost = getMainActivity().supportFragmentManager
                    .findFragmentById(R.id.detail_view)
                if (!(backupHost is PhoneNavHostFragment && backupHost.navigateToBackup())) {
                    navigatePhoneNavRoot(PhoneNavRoutes.BACKUP)
                }
            }
        }
        getMainActivity().showContent()
        return !isDialogItem(itemId)
    }

    /**
     * EPG drawer root needs default bouquet ref/name extras (not a plain route map entry).
     */
    protected fun navigateToEpg() {
        val epgArgs = Bundle()
        val ref = DreamDroid.getCurrentProfile().defaultBouquetTv
        epgArgs.putString(Event.KEY_SERVICE_REFERENCE, ref)
        val name = DreamDroid.getCurrentProfile().defaultBouquetTvName
        epgArgs.putString(Event.KEY_SERVICE_NAME, name)

        val detail = getMainActivity().supportFragmentManager
            .findFragmentById(R.id.detail_view)
        if (detail is PhoneNavHostFragment && detail.navigateToEpg(ref, name)) {
            return
        }
        clearBackStack()
        getMainActivity().showDetails(
            PhoneNavHostFragment.newInstance(PhoneNavRoutes.EPG, epgArgs),
        )
    }

    /**
     * @param time
     * @param action
     * @param enabled
     */
    fun onSetSleepTimer(time: String?, action: String?, enabled: Boolean) {
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("cmd", SleepTimer.CMD_SET))
        params.add(NameValuePair("time", time))
        params.add(NameValuePair("action", action))

        if (enabled) {
            params.add(NameValuePair("enabled", Python.TRUE))
        } else {
            params.add(NameValuePair("enabled", Python.FALSE))
        }

        execSleepTimerTask(params, false)
    }

    protected fun getSleepTimer(showDialogOnFinish: Boolean) {
        val params = ArrayList<NameValuePair>()
        execSleepTimerTask(params, showDialogOnFinish)
    }

    private fun onSleepTimerSet(
        success: Boolean,
        result: ExtendedHashMap,
        openDialog: Boolean,
        errorText: String?,
    ) {
        if (success) {
            if (openDialog) {
                getMainActivity().showDialogFragment(SleepTimerDialog.newInstance(result), "sleeptimer_dialog")
                return
            }
            val text = result.getString(SleepTimer.KEY_TEXT)
            showToast(text)
        } else {
            showToast(getString(R.string.error))
        }
    }

    fun execSimpleResultTask(handler: SimpleResultRequestHandler, params: ArrayList<NameValuePair>) {
        mSimpleResultJob?.cancel(null)
        mSimpleResultJob = mActivity.launchSimpleResultLoad(handler, params) { success, result, http ->
            mSimpleResultJob = null
            onSimpleResult(success, result, http)
        }
    }

    private fun onSimpleResult(success: Boolean, result: ExtendedHashMap, http: SimpleHttpClient) {
        var toastText = getString(R.string.get_content_error)
        val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)

        if (stateText != null && stateText != "") {
            toastText = stateText
        } else if (http.hasError()) {
            toastText = http.getErrorText(getContext()) ?: toastText
        }

        showToast(toastText)
    }

    /**
     * @param params
     */
    @Suppress("UNCHECKED_CAST")
    protected fun execSleepTimerTask(params: ArrayList<NameValuePair>, showDialogOnFinish: Boolean) {
        mSleepTimerJob?.cancel(null)

        mSleepTimerJob = mActivity.launchSleepTimerLoad(
            params,
            showDialogOnFinish,
            mActivity,
        ) { success, result, openDialog, errorText ->
            mSleepTimerJob = null
            onSleepTimerSet(success, result, openDialog, errorText)
        }
    }

    /**
     * @param state The powerstate to set. For example defined in
     * `helpers.enigma2.PowerState.STATE_*`
     */
    protected fun setPowerState(state: String) {
        mPowerStateJob?.cancel(null)

        mPowerStateJob = mActivity.launchPowerStateSetLoad(
            state,
            mActivity,
        ) { success, result, errorText ->
            mPowerStateJob = null
            onPowerStateSet(success, result, errorText)
        }
    }

    /**
     * Send a message to the target device which will be shown on TV
     *
     * @param text    The message text
     * @param type    Type of the message as defined in
     * `helpers.enigma2.Message.STATE_*`
     * @param timeout Timeout for the message, 0 means no timeout will occur
     */
    fun onSendMessage(text: String?, type: String?, timeout: String?) {
        val msg = ExtendedHashMap()
        msg.put(Message.KEY_TEXT, text)
        msg.put(Message.KEY_TYPE, type)
        msg.put(Message.KEY_TIMEOUT, timeout)

        execSimpleResultTask(MessageRequestHandler(), Message.getParams(msg))
    }

    fun setAvailableFeatures() {
        // TODO implement feature-handling for list-navigation
    }

    protected fun showToast(toastText: String?) {
        val toast = Toast.makeText(getMainActivity(), toastText, Toast.LENGTH_LONG)
        toast.show()
    }

    fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        onNavigationItemClick(action)
    }

    fun getContext(): Context = getMainActivity()

    companion object {
        @JvmField
        protected val sDialogItemIds: IntArray = intArrayOf(
            R.id.menu_navigation_sleeptimer,
            R.id.menu_navigation_message,
            R.id.menu_navigation_power,
            R.id.menu_navigation_about,
            R.id.menu_navigation_changelog,
        )

        /** Drawer menu ids that open a PhoneNavHost root (no extras). EPG is separate. */
        private val sNavRootRoutes: SparseArray<String> = SparseArray<String>().apply {
            put(R.id.menu_navigation_services, PhoneNavRoutes.HUB)
            put(R.id.menu_navigation_device_info, PhoneNavRoutes.DEVICE_INFO)
            put(R.id.menu_navigation_current, PhoneNavRoutes.CURRENT)
            put(R.id.menu_navigation_remote, PhoneNavRoutes.REMOTE)
            put(R.id.menu_navigation_settings, PhoneNavRoutes.SETTINGS)
            put(R.id.menu_navigation_screenshot, PhoneNavRoutes.SCREENSHOT)
            put(R.id.menu_navigation_profiles, PhoneNavRoutes.PROFILES)
            put(R.id.menu_navigation_signal, PhoneNavRoutes.SIGNAL)
            put(R.id.menu_navigation_zap, PhoneNavRoutes.ZAP)
        }
    }
}
