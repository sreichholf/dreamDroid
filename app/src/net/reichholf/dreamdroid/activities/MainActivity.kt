/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.ProfileChangedListener
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.launchCheckProfileLoad
import net.reichholf.dreamdroid.enigma.launchVolumeSetLoad
import net.reichholf.dreamdroid.helpers.LocalNetworkPermission
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile
import net.reichholf.dreamdroid.helpers.enigma2.shouldConsumeVolumeKey
import net.reichholf.dreamdroid.helpers.enigma2.volumeCommandForKey
import net.reichholf.dreamdroid.room.AppDatabase
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
import net.reichholf.dreamdroid.ui.nav.StartScreen
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.SESSION_REACHABILITY_INTERVAL_MS
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckCheckingUi
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckFailedUi
import net.reichholf.dreamdroid.ui.settings.SettingsState
import net.reichholf.dreamdroid.ui.setup.SetupAssistantScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * @author sre
 */
class MainActivity :
    BaseActivity(),
    ProfileChangedListener,
    DialogActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    DrawerRouteHighlighter {

    private var drawerOpen by mutableStateOf(false)
    private var profileName by mutableStateOf("")
    private var shellWasPaused: Boolean = false

    private var checkProfileJob: Job? = null
    private var reachabilityJob: Job? = null
    private var volumeSetJob: Job? = null
    private var showingSetup: Boolean = false
    private var shellCallbackRegistered: Boolean = false
    private var lanGranted by mutableStateOf(false)

    private var navigationHelper: NavigationHelper? = null
    private val drawerListState = DrawerListState()
    private val destinationController = ShellDestinationBarController()
    private val fabController = ShellFabController()
    val phoneNav: PhoneNavHostState by viewModels()

    private var phoneShellReady: Boolean = false
    private var snackbar: Snackbar? = null

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

    private fun dismissSnackbar() {
        snackbar?.dismiss()
        snackbar = null
    }

    private fun showProfileCheckChecking(message: String) {
        dismissSnackbar()
        val ui = ProfileCheckUi.Checking(message)
        phoneNav.navigateToProfileCheck(ui)
    }

    private fun showProfileCheckFailed(result: ProfileCheckResult) {
        dismissSnackbar()
        openStartOnProfileSuccess = true
        var error: String? = getString(result.errorTextId)
        if (result.errorTextExt.isNotEmpty()) {
            error = result.errorTextExt
        }
        if (error.isNullOrEmpty()) {
            error = getString(result.errorTextId)
        }
        val p = DreamDroid.getCurrentProfile()
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
        val p = DreamDroid.getCurrentProfile()
        p.cachedDeviceInfo = null
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
        val hasCache = hasUseDrivenCache(DreamDroid.getCurrentProfile(), this)
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
            dismissSnackbar()
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

    override fun requestLocalNetworkOnCreate(): Boolean = DreamDroid.hasCurrentProfile()

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        super.onCreate(savedInstanceState)
        if (!DreamDroid.hasCurrentProfile()) {
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
        if (!DreamDroid.ensureCurrentProfile(this)) {
            reachabilityJob?.cancel()
            reachabilityJob = null
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
                    viewModel = viewModel(),
                    localNetworkGranted = lanGranted,
                    onRequestLocalNetwork = { ensureLocalNetworkPermission() },
                    onSave = { profile ->
                        val id = AppDatabase.profilesBlocking(this).addProfile(profile).toInt()
                        profile.id = id
                        DreamDroid.setCurrentProfile(this, id, true)
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
        startSessionReachabilityProbe()
        DreamDroid.setCurrentProfileChangedListener(this)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        showChangeLog(true)
        handleSearchIntent(intent)
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
        reachabilityJob?.cancel()
        reachabilityJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    val active = DreamDroid.currentProfileOrNull()
                    if (active == null) {
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
                            val profile = DreamDroid.currentProfileOrNull() ?: active
                            profile.cachedDeviceInfo = null
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
        if (showingSetup || !DreamDroid.hasCurrentProfile()) {
            return
        }
        onProfileChanged(DreamDroid.getCurrentProfile(), true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSearchIntent(intent)
    }

    /** System ACTION_SEARCH — same path as submitting the destination SearchBar. */
    private fun handleSearchIntent(intent: Intent?) {
        if (intent == null || Intent.ACTION_SEARCH != intent.action) {
            return
        }
        if (!phoneShellReady) {
            return
        }
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        phoneNav.navigateToEpgSearch(query)
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
            onProfileChanged(DreamDroid.getCurrentProfile(), true)
        }
    }

    override fun onDestroy() {
        navigationHelper?.onDestroy()
        navigationHelper = null
        PreferenceManager.getDefaultSharedPreferences(
            this
        ).unregisterOnSharedPreferenceChangeListener(this)
        if (DreamDroid.getCurrentProfileChangedListener() === this) {
            DreamDroid.setCurrentProfileChangedListener(null)
        }
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
        onProfileChanged(DreamDroid.getCurrentProfile(), isResume)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.search, menu)
        return true
    }

    private fun initViews() {
        setContent {
            DreamDroidTheme {
                val status by phoneNav.connectionStatusFlow().collectAsState()
                PhoneShell(
                    drawerListState = drawerListState,
                    drawerOpen = drawerOpen,
                    onDrawerOpenChange = { open ->
                        if (open && !drawerOpen) {
                            dismissSnackbar()
                        }
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
                    onToolbarReady = { toolbar ->
                        if (supportActionBar == null) {
                            setSupportActionBar(toolbar)
                            supportActionBar?.setDisplayHomeAsUpEnabled(true)
                            supportActionBar?.setHomeButtonEnabled(true)
                        }
                    }
                ) {
                    PhoneNavHost(handle = phoneNav)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                toggle()
                return true
            }

            R.id.action_search -> {
                if (phoneShellReady) {
                    phoneNav.navigateToEpgSearch("")
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun isNavigationDrawerVisible(): Boolean = drawerOpen

    fun toggle() {
        drawerOpen = !drawerOpen
    }

    fun showContent() {
        drawerOpen = false
    }

    /*
     * (non-Javadoc)
     *
     * @see net.reichholf.dreamdroid.OnActiveProfileChangedListener#
     * onActiveProfileChanged(net.reichholf.dreamdroid.Profile)
     */
    override fun onProfileChanged(p: Profile) {
        onProfileChanged(p, false)
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
        if (p.cachedDeviceInfo == null) {
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
        profileName = DreamDroid.getCurrentProfile().name.orEmpty()
    }

    private fun bindDrawerConnectionChip() {
        if (!phoneShellReady) {
            return
        }
        profileName = DreamDroid.getCurrentProfile().name.orEmpty()
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
            .getBoolean(SettingsState.KEY_VOLUME_CONTROL, false)

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

    /*
     * Dialog action routing for Compose choice / progress / connection dialogs.
     * EPG/movie detail sheets are in-composition ModalBottomSheet (Phase 2.1g-ii-d).
     */
    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        if (phoneShellReady) {
            phoneNav.composeDialogActionListener?.onDialogAction(action, details, dialogTag)
        }
        super.onDialogAction(action, details, dialogTag)
    }

    fun onSetSleepTimer(time: String, action: String, enabled: Boolean) {
        navigationHelper?.onSetSleepTimer(time, action, enabled)
    }

    fun onDrawerPowerChoice(action: Int) {
        navigationHelper?.onDialogAction(action, null, null)
    }

    fun onSendMessage(text: String, type: String, timeout: String) {
        navigationHelper?.onSendMessage(text, type, timeout)
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
