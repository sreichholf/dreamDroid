/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities

import android.content.Intent
import android.os.Bundle
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.helpers.LocalNetworkPermission
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.drawer.DrawerHighlight
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.DrawerRouteHighlighter
import net.reichholf.dreamdroid.ui.nav.NavigationHelper
import net.reichholf.dreamdroid.ui.nav.PhoneNavHost
import net.reichholf.dreamdroid.ui.nav.PhoneNavHostState
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.PhoneNavigator
import net.reichholf.dreamdroid.ui.nav.PhoneShell
import net.reichholf.dreamdroid.ui.nav.ProfileCheckOutcome
import net.reichholf.dreamdroid.ui.nav.ShellDestinationBarController
import net.reichholf.dreamdroid.ui.nav.ShellFabController
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShellTopBarController
import net.reichholf.dreamdroid.ui.nav.ShellUiState
import net.reichholf.dreamdroid.ui.nav.ShellViewModel
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.StartScreen
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.session.SESSION_REACHABILITY_INTERVAL_MS
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.setup.SetupAssistantScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * @author sre
 */
@AndroidEntryPoint
class MainActivity :
    BaseActivity(),
    DialogActionListener,
    DrawerRouteHighlighter {

    private var drawerOpen by mutableStateOf(false)
    private var shellWasPaused: Boolean = false

    private var showingSetup: Boolean = false
    private var shellCallbackRegistered: Boolean = false
    private var lanGranted by mutableStateOf(false)

    private var navigationHelper: NavigationHelper? = null
    private val drawerListState = DrawerListState()
    private val destinationController = ShellDestinationBarController()
    private val fabController = ShellFabController()
    private val topBarController = ShellTopBarController()

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var sessions: SessionConnectionHolder

    private var themeJob: Job? = null

    private val phoneNavState: PhoneNavHostState by viewModels()

    /** Created on first use, after Hilt injected; the activity is its drawer highlighter. */
    val phoneNav: PhoneNavigator by lazy {
        PhoneNavigator(phoneNavState, this, this, profiles, settings, sessions)
    }
    val shellActions: ShellViewModel by viewModels()

    private var phoneShellReady: Boolean = false

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
            if (settings.current().confirmAppClose) {
                phoneNav.requestLeaveConfirm()
            } else {
                finish()
            }
        }
    }

    private fun leaveProfileCheckGate(isFirstStart: Boolean) {
        if (phoneNav.isOnProfileCheckRoute()) {
            val route = if (isFirstStart) {
                PhoneNavRoutes.PROFILES
            } else {
                StartScreen.navRoute(settings.current().startScreen)
            }
            // Drop the gate so Back from the service list does not return to the check.
            phoneNav.navigateReplacingProfileCheck(route)
            return
        }
        if (isFirstStart) {
            navigationHelper!!.navigateTo(R.id.menu_navigation_profiles)
        } else {
            navigationHelper!!.navigateTo(StartScreen.menuId(settings.current().startScreen))
        }
    }

    private fun isPaused(): Boolean = !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    /** Navigation for the profile check that [shellActions] runs; only while resumed. */
    private fun onProfileCheckEffects(state: ShellUiState) {
        state.profileCheckStarted?.let { start ->
            shellActions.onProfileCheckStartHandled()
            if (start.showGate) {
                phoneNav.navigateToProfileCheck()
            }
            navigationHelper?.onProfileChanged()
        }
        val outcome = state.profileCheckOutcome ?: return
        ensureNavigationHelper()
        navigationHelper!!.setAvailableFeatures()
        when (outcome) {
            is ProfileCheckOutcome.Failed -> phoneNav.navigateToProfileCheck()

            is ProfileCheckOutcome.Leave ->
                if (outcome.offGateToo || phoneNav.isOnProfileCheckRoute()) {
                    leaveProfileCheckGate(outcome.firstStart)
                }
        }
        if (outcome.firstStart && !isNavigationDrawerVisible()) {
            toggle()
        }
        shellActions.onProfileCheckOutcomeHandled()
    }

    override fun requestLocalNetworkOnCreate(): Boolean = profiles.hasCurrent()

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
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                shellActions.uiState.collect { state ->
                    if (phoneShellReady) {
                        onProfileCheckEffects(state)
                    }
                }
            }
        }
        startSessionReachabilityProbe()
        if (!profiles.hasCurrent()) {
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
        if (!profiles.ensureCurrent()) {
            shellActions.cancelCheck()
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
                        shellActions.onSetupFinished()
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

        if (!phoneNav.hasSavedStartRoute()) {
            phoneNav.setStartRoute(StartScreen.navRoute(settings.current().startScreen))
        }
        phoneShellReady = true
        // Switches from now on are checked; the setup assistant's switch came before.
        shellActions.start()
        initViews()
        followThemeSetting()
        showChangeLog(true)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            checkNavigationHelper()
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
                    if (!showingSetup && shellActions.probeReachability()) {
                        navigationHelper?.setAvailableFeatures()
                    }
                    delay(SESSION_REACHABILITY_INTERVAL_MS)
                }
            }
        }
    }

    override fun onLocalNetworkPermissionGranted() {
        lanGranted = true
        if (showingSetup || !profiles.hasCurrent()) {
            return
        }
        shellActions.checkActiveProfile()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /**
     * open the change log dialog
     *
     * @param onUpdateOnly if true, only show the change log after an app update
     */
    fun showChangeLog(onUpdateOnly: Boolean) {
        val updated = settings.lastVersionCode < BuildConfig.VERSION_CODE
        if (updated) {
            settings.lastVersionCode = BuildConfig.VERSION_CODE
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
            checkNavigationHelper()
            return
        }
        if (shellWasPaused) {
            shellWasPaused = false
            shellActions.checkActiveProfile()
        }
    }

    override fun onDestroy() {
        navigationHelper = null
        super.onDestroy()
    }

    private fun ensureNavigationHelper() {
        if (navigationHelper != null) {
            return
        }
        navigationHelper = NavigationHelper(this, drawerListState)
    }

    private fun checkNavigationHelper(): Boolean {
        if (navigationHelper != null) {
            return false
        }
        ensureNavigationHelper()
        shellActions.checkActiveProfile()
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
        shellActions.cancelCheck()
        super.onStop()
    }

    private fun initViews() {
        setContent {
            DreamDroidTheme {
                val status by phoneNav.connectionStatusFlow().collectAsState()
                val shellUiState by shellActions.uiState.collectAsStateWithLifecycle()
                PhoneShell(
                    drawerListState = drawerListState,
                    drawerOpen = drawerOpen,
                    onDrawerOpenChange = { open ->
                        drawerOpen = open
                    },
                    profileName = shellUiState.profileName,
                    connectionLabel = stringResource(status.chipLabelRes()),
                    boxActionsBlocked = status.blocksMutations,
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!shellActions.controlsReceiverVolume(keyCode)) {
            return super.onKeyDown(keyCode, event)
        }
        if (phoneShellReady) {
            phoneNav.runOnlineOnly { shellActions.onVolumeKey(keyCode) }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!shellActions.controlsReceiverVolume(keyCode)) {
            return super.onKeyUp(keyCode, event)
        }
        return true
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

    /** Applies a changed theme setting; a resumed shell recreates to show it. */
    private fun followThemeSetting() {
        if (themeJob != null) {
            return
        }
        themeJob = lifecycleScope.launch {
            settings.settings.map { it.themeType }.distinctUntilChanged().drop(1).collect {
                DreamDroid.setTheme(this@MainActivity)
                if (!isPaused()) {
                    recreate()
                }
            }
        }
    }
}
