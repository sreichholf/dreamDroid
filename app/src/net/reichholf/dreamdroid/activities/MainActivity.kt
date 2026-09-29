/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.launchCheckProfileLoad
import net.reichholf.dreamdroid.enigma.launchVolumeSetLoad
import net.reichholf.dreamdroid.helpers.LocalNetworkPermission
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile
import net.reichholf.dreamdroid.helpers.enigma2.shouldConsumeVolumeKey
import net.reichholf.dreamdroid.helpers.enigma2.volumeCommandForKey
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.drawer.DrawerHighlight
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.DrawerRouteHighlighter
import net.reichholf.dreamdroid.ui.nav.NavigationHelper
import net.reichholf.dreamdroid.ui.nav.PhoneNavHost
import net.reichholf.dreamdroid.ui.nav.PhoneNavHostState
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.PhoneShell
import net.reichholf.dreamdroid.ui.nav.ShellDestinationBarController
import net.reichholf.dreamdroid.ui.nav.ShellFabController
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShellTopBarController
import net.reichholf.dreamdroid.ui.nav.ShellViewModel
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.StartScreen
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.SESSION_REACHABILITY_INTERVAL_MS
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckCheckingUi
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckFailedUi
import net.reichholf.dreamdroid.ui.setup.SetupAssistantScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * @author sre
 */
@AndroidEntryPoint
class MainActivity :
    BaseActivity(),
    DialogActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    DrawerRouteHighlighter {

    private var drawerOpen by mutableStateOf(false)
    private var profileName by mutableStateOf("")
    private var shellWasPaused: Boolean = false

    private var checkProfileJob: Job? = null
    private var volumeSetJob: Job? = null
    private var showingSetup: Boolean = false
    private var shellCallbackRegistered: Boolean = false
    private var lanGranted by mutableStateOf(false)

    private var navigationHelper: NavigationHelper? = null
    private val drawerListState = DrawerListState()
    private val destinationController = ShellDestinationBarController()
    private val fabController = ShellFabController()
    private val topBarController = ShellTopBarController()
    val phoneNav: PhoneNavHostState by viewModels()
    val shellActions: ShellViewModel by viewModels()

    private var phoneShellReady: Boolean = false

    /** When true, a successful profile check opens the start route (after Recheck). */
    private var openStartOnProfileSuccess: Boolean = false

    private lateinit var currentProfile: Profile

    /**
     * Lowest-priority back handler: drawer close, then NavHost pop, then optional leave-confirm.
     * Registered early in [onCreate] so Compose [BackHandler]s stay higher priority.
     */
    private val leaveAppCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isNavigationDrawerVisible()) {
                toggle()
                return
            }
            if (phoneNav.popNavBackStack()) {
                return
            }
            val shouldConfirm = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                .getBoolean(DreamDroid.PREFS_KEY_CONFIRM_APP_CLOSE, true)
            if (shouldConfirm) {
                phoneNav.requestLeaveConfirm()
            } else {
                finish()
            }
        }
    }

    private fun showProfileCheckChecking(message: String) {
        val ui = ProfileCheckUi.Checking(message)
        phoneNav.navigateToProfileCheck(ui)
    }

    private fun showProfileCheckFailed(result: ProfileCheckResult) {
        openStartOnProfileSuccess = true
        var error: String? = getString(result.errorTextId)
        if (result.errorTextExt.isNotEmpty()) {
            error = result.errorTextExt
        }
        if (error.isNullOrEmpty()) {
            error = getString(result.errorTextId)
        }
        val p = ProfileRepository.get().requireCurrent()
        val title = String.format("%s@%s:%s", p.user, p.host, p.port)
        val ui = ProfileCheckUi.Failed(title = title, message = error.orEmpty())
        phoneNav.navigateToProfileCheck(ui)
    }

    private fun updateProfileCheckChecking(message: String) {
        if (phoneNav.isOnProfileCheckRoute()) {
            phoneNav.updateProfileCheckUi(ProfileCheckUi.Checking(message))
        }
    }

    fun recheckProfileAfterFailure() {
        // Keep openStartOnProfileSuccess so a later success opens the start route.
        showProfileCheckChecking(getString(R.string.checking_connection))
        val p = ProfileRepository.get().requireCurrent()
        ProfileRepository.get().setDeviceInfo(p, null)
        onProfileChanged(p, true)
    }

    fun openProfilesFromProfileCheckFailed() {
        openStartOnProfileSuccess = false
        if (phoneNav.isOnProfileCheckRoute()) {
            // Keep the gate under Profiles so Back returns to the check.
            phoneNav.navigateAboveProfileCheck(PhoneNavRoutes.PROFILES)
            return
        }
        navigationHelper?.navigateTo(R.id.menu_navigation_profiles)
    }

    private fun leaveProfileCheckGate(isFirstStart: Boolean) {
        if (phoneNav.isOnProfileCheckRoute()) {
            val route = if (isFirstStart) {
                PhoneNavRoutes.PROFILES
            } else {
                StartScreen.navRoute(this)
            }
            // Drop the gate so Back from the service list does not return to the check.
            phoneNav.navigateReplacingProfileCheck(route)
            return
        }
        if (isFirstStart) {
            navigationHelper!!.navigateTo(R.id.menu_navigation_profiles)
        } else {
            navigationHelper!!.navigateTo(StartScreen.menuId(this))
        }
    }

    private fun isPaused(): Boolean = !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun getProfileCheckContext(): Context = this

    private fun onProfileCheckProgress(state: String) {
        SessionConnectionHolder.shared.beginChecking()
        bindDrawerConnectionChip()
        updateProfileCheckChecking(state)
    }

    fun onProfileChecked(result: ProfileCheckResult) {
        val hasCache = hasUseDrivenCache(ProfileRepository.get().requireCurrent(), this)
        // Apply before any UI/helper gate so a finished check cannot leave Checking
        // stuck (paused window, or helper recreated between onPause and RESUMED).
        SessionConnectionHolder.shared.applyProfileCheckResult(result, hasCache)
        bindDrawerConnectionChip()
        if (isPaused()) {
            return
        }
        ensureNavigationHelper()
        navigationHelper!!.setAvailableFeatures()
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstStart = sp.getBoolean(DreamDroid.PREFS_KEY_FIRST_START, true)

        if (result.hasError && !result.isSoftError) {
            if (shouldShowProfileCheckFailedUi(hasCache, result.failure)) {
                showProfileCheckFailed(result)
            } else if (phoneNav.isOnProfileCheckRoute()) {
                leaveProfileCheckGate(isFirstStart)
            }
        } else {
            val openStart = openStartOnProfileSuccess
            openStartOnProfileSuccess = false
            val onGate = phoneNav.isOnProfileCheckRoute()
            if (onGate || openStart) {
                // Leave PROFILE_CHECK on the back stack so Back returns to the gate.
                leaveProfileCheckGate(isFirstStart)
            } else if (isFirstStart) {
                navigationHelper!!.navigateTo(R.id.menu_navigation_profiles)
            }
        }

        if (isFirstStart) {
            if (!isNavigationDrawerVisible()) {
                toggle()
            }
            sp.edit().putBoolean(DreamDroid.PREFS_KEY_FIRST_START, false).apply()
        }
    }

    override fun requestLocalNetworkOnCreate(): Boolean = ProfileRepository.get().hasCurrent()

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                shellActions.uiState.collect { state ->
                    state.sleepTimerEffect?.let { timer ->
                        shellActions.onSleepTimerEffectHandled()
                        phoneNav.navigateToSleepTimer(timer)
                    }
                    state.profileSwitchEffect?.let { profile ->
                        shellActions.onProfileSwitchHandled()
                        // The setup assistant activates its profile before the shell
                        // exists; startPhoneShell checks that profile itself.
                        if (phoneShellReady) {
                            onProfileChanged(profile, false)
                        }
                    }
                }
            }
        }
        startSessionReachabilityProbe()
        if (!ProfileRepository.get().hasCurrent()) {
            showSetupAssistant()
            return
        }
        startPhoneShell()
    }

    override fun onStart() {
        super.onStart()
        if (showingSetup || !phoneShellReady) {
            return
        }
        if (!ProfileRepository.get().ensureCurrent()) {
            checkProfileJob?.cancel()
            checkProfileJob = null
            showSetupAssistant()
        }
    }

    private fun showSetupAssistant() {
        showingSetup = true
        lanGranted = LocalNetworkPermission.isGranted(this)
        setContent {
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = hiltViewModel(),
                    localNetworkGranted = lanGranted,
                    onRequestLocalNetwork = { ensureLocalNetworkPermission() },
                    onFinished = {
                        PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putBoolean(DreamDroid.PREFS_KEY_FIRST_START, false)
                            .apply()
                        startPhoneShell()
                    },
                    onLeave = { finish() }
                )
            }
        }
    }

    private fun startPhoneShell() {
        showingSetup = false
        // Register before Compose so destination BackHandlers outrank leave-confirm.
        if (!shellCallbackRegistered) {
            onBackPressedDispatcher.addCallback(this, leaveAppCallback)
            shellCallbackRegistered = true
        }
        ensureLocalNetworkPermission()

        currentProfile = Profile.getDefault()
        phoneNav.attach(this, this) // activity is LifecycleOwner and DrawerRouteHighlighter
        if (!phoneNav.hasSavedStartRoute()) {
            phoneNav.setStartRoute(StartScreen.navRoute(this))
        }
        phoneShellReady = true
        initViews()
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        showChangeLog(true)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            checkNavigationHelper(
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            )
        }
    }

    /**
     * While resumed, ping the box every 30s (and immediately on resume) so Online
     * can become Offline and Unreachable Offline can recover without reselecting
     * the profile. Auth / illegal host are not polled. Does not flash Checking.
     */
    private fun startSessionReachabilityProbe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    val active = ProfileRepository.get().current.value
                    if (active == null || showingSetup) {
                        delay(SESSION_REACHABILITY_INTERVAL_MS)
                        continue
                    }
                    val ran = probeSessionReachabilityIfNeeded(
                        holder = SessionConnectionHolder.shared,
                        hasCache = hasUseDrivenCache(
                            active,
                            this@MainActivity
                        ),
                        isBusy = { checkProfileJob != null },
                        check = {
                            val profile = ProfileRepository.get().current.value ?: active
                            ProfileRepository.get().setDeviceInfo(profile, null)
                            withContext(Dispatchers.IO) {
                                CheckProfile.checkProfile(profile, this@MainActivity)
                            }
                        }
                    )
                    if (ran) {
                        bindDrawerConnectionChip()
                        navigationHelper?.setAvailableFeatures()
                    }
                    delay(SESSION_REACHABILITY_INTERVAL_MS)
                }
            }
        }
    }

    override fun onLocalNetworkPermissionGranted() {
        lanGranted = true
        if (showingSetup || !ProfileRepository.get().hasCurrent()) {
            return
        }
        onProfileChanged(ProfileRepository.get().requireCurrent(), true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        topBarController.title = title?.toString().orEmpty()
    }

    /**
     * open the change log dialog
     *
     * @param onUpdateOnly if true, only show the change log after an app update
     */
    fun showChangeLog(onUpdateOnly: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastVersionCode = preferences.getInt(DreamDroid.PREFS_KEY_LAST_VERSION_CODE, 0)
        val updated = lastVersionCode < BuildConfig.VERSION_CODE
        if (updated) {
            val editor = preferences.edit()
            editor.putInt(DreamDroid.PREFS_KEY_LAST_VERSION_CODE, BuildConfig.VERSION_CODE)
            editor.apply()
        }
        if (updated || !onUpdateOnly) {
            phoneNav.navigateToChangelog()
        }
    }

    override fun dispatchActivityResultToNavHandle(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (!phoneShellReady) {
            return false
        }
        phoneNav.onHostActivityResult(requestCode, resultCode, data)
        return true
    }

    override fun onResume() {
        super.onResume()
        if (showingSetup || !phoneShellReady) {
            return
        }
        if (navigationHelper == null) {
            checkNavigationHelper(true)
            return
        }
        if (shellWasPaused) {
            shellWasPaused = false
            onProfileChanged(ProfileRepository.get().requireCurrent(), true)
        }
    }

    override fun onDestroy() {
        navigationHelper = null
        PreferenceManager.getDefaultSharedPreferences(
            this
        ).unregisterOnSharedPreferenceChangeListener(this)
        if (phoneShellReady) {
            phoneNav.detach()
        }
        super.onDestroy()
    }

    private fun ensureNavigationHelper() {
        if (navigationHelper != null) {
            return
        }
        navigationHelper = NavigationHelper(this, drawerListState)
    }

    private fun checkNavigationHelper(): Boolean = checkNavigationHelper(false)

    private fun checkNavigationHelper(isResume: Boolean): Boolean {
        if (navigationHelper != null) {
            return false
        }
        ensureNavigationHelper()
        onProfileChanged(ProfileRepository.get().requireCurrent(), isResume)
        return true
    }

    override fun highlightDrawerForRoute(route: String?, previousRoute: String?) {
        val itemId = DrawerHighlight.itemIdForRoute(
            route,
            previousRoute
        ) ?: return
        if (itemId == R.id.menu_none) {
            drawerListState.clearSelection()
        } else {
            drawerListState.select(itemId)
        }
    }

    override fun onPause() {
        shellWasPaused = true
        super.onPause()
    }

    override fun onStop() {
        checkProfileJob?.cancel(null)
        checkProfileJob = null
        SessionConnectionHolder.shared.cancelChecking()
        bindDrawerConnectionChip()
        super.onStop()
    }

    private fun initViews() {
        setContent {
            DreamDroidTheme {
                val status by phoneNav.connectionStatusFlow().collectAsState()
                PhoneShell(
                    drawerListState = drawerListState,
                    drawerOpen = drawerOpen,
                    onDrawerOpenChange = { open ->
                        drawerOpen = open
                    },
                    profileName = profileName,
                    connectionLabel = stringResource(status.chipLabelRes()),
                    onProfileClick = {
                        checkNavigationHelper()
                        navigationHelper?.navigateTo(R.id.menu_navigation_profiles)
                    },
                    onDrawerItemClick = { itemId ->
                        checkNavigationHelper()
                        navigationHelper?.navigateTo(itemId)
                    },
                    onNavigationClick = { toggle() },
                    destinationController = destinationController,
                    fabController = fabController,
                    topBarController = topBarController,
                    trailingTopBarActions = listOf(
                        ShellTopBarAction(
                            id = R.id.action_search,
                            label = stringResource(R.string.epg_search),
                            iconRes = R.drawable.ic_action_search,
                            onClick = {
                                if (phoneShellReady) {
                                    phoneNav.navigateToEpgSearch("")
                                }
                            }
                        )
                    )
                ) {
                    val shellUiState by shellActions.uiState.collectAsStateWithLifecycle()
                    ShowShellUserMessage(
                        shellUiState.userMessage,
                        shellActions::onMessageShown
                    )
                    PhoneNavHost(handle = phoneNav)
                }
            }
        }
    }

    fun isNavigationDrawerVisible(): Boolean = drawerOpen

    fun toggle() {
        drawerOpen = !drawerOpen
    }

    fun showContent() {
        drawerOpen = false
    }

    fun onProfileChanged(p: Profile, isResuming: Boolean) {
        if (!isResuming && isPaused()) {
            return
        }

        if (p.id != currentProfile.id) {
            SessionConnectionHolder.shared.resetForProfileChange()
            bindDrawerConnectionChip()
        }
        setProfileName()
        if (ProfileRepository.get().deviceInfo(p) == null) {
            if (p == currentProfile && checkProfileJob != null) {
                return
            }
            currentProfile = p
            checkProfileJob?.cancel(null)
            checkProfileJob = null
            val hasCache = hasUseDrivenCache(p, this)
            if (shouldShowProfileCheckCheckingUi(hasCache)) {
                showProfileCheckChecking(getString(R.string.checking_connection))
            }
            SessionConnectionHolder.shared.beginChecking()
            bindDrawerConnectionChip()
            checkProfileJob = launchCheckProfileLoad(
                p,
                getProfileCheckContext(),
                { state -> onProfileCheckProgress(state) },
                { result ->
                    checkProfileJob = null
                    if (result != null) {
                        onProfileChecked(result)
                    }
                }
            )
        } else {
            currentProfile = p
            onProfileChecked(CheckProfile.checkProfile(p, this))
        }
        navigationHelper?.onProfileChanged()
    }

    /**
     *
     */
    fun setProfileName() {
        profileName = ProfileRepository.get().requireCurrent().name.orEmpty()
    }

    private fun bindDrawerConnectionChip() {
        if (!phoneShellReady) {
            return
        }
        profileName = ProfileRepository.get().requireCurrent().name.orEmpty()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!shouldConsumeVolumeKey(keyCode, volumeControlEnabled())) {
            return super.onKeyDown(keyCode, event)
        }
        sendReceiverVolume(keyCode)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!shouldConsumeVolumeKey(keyCode, volumeControlEnabled())) {
            return super.onKeyUp(keyCode, event)
        }
        return true
    }

    private fun volumeControlEnabled(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(DreamDroid.PREFS_KEY_VOLUME_CONTROL, false)

    private fun sendReceiverVolume(keyCode: Int) {
        if (volumeSetJob?.isActive == true) {
            return
        }
        val command = volumeCommandForKey(keyCode) ?: return
        if (!phoneShellReady) {
            return
        }
        phoneNav.runOnlineOnly {
            volumeSetJob = launchVolumeSetLoad(
                listOf(NameValuePair("set", command))
            ) { _, _ -> }
        }
    }

    fun onSetSleepTimer(time: String, action: String, enabled: Boolean) {
        phoneNav.runOnlineOnly {
            shellActions.setSleepTimer(time, action, enabled)
        }
    }

    fun onDrawerPowerChoice(action: Int) {
        navigationHelper?.onDialogAction(action)
    }

    fun onSendMessage(text: String, type: String, timeout: String) {
        phoneNav.runOnlineOnly {
            shellActions.sendMessage(text, type, timeout)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.w(DreamDroid.LOG_TAG, key ?: "")
        if (DreamDroid.PREFS_KEY_THEME_TYPE == key) {
            DreamDroid.setTheme(this)
            if (!isPaused()) {
                recreate()
            }
        }
    }
}
