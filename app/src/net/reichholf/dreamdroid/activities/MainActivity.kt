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
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.launchCheckProfileLoad
import net.reichholf.dreamdroid.fragment.helper.NavigationHelper
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.drawer.DrawerHighlight
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.drawer.DrawerRouteHighlighter
import net.reichholf.dreamdroid.ui.nav.PhoneNavHostState
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.StartScreen
import net.reichholf.dreamdroid.ui.nav.bindPhoneNavHost
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SESSION_REACHABILITY_INTERVAL_MS
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckCheckingUi
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckFailedUi

/**
 * @author sre
 */
class MainActivity :
    BaseActivity(),
    MultiPaneHandler,
    ProfileChangedListener,
    DialogActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    DrawerRouteHighlighter {

    private var slider: Boolean = false
    private var isDrawerOpenNotified: Boolean = false
    private lateinit var activeProfile: TextView
    private lateinit var connectionState: TextView

    private var checkProfileJob: Job? = null

    private var navigationHelper: NavigationHelper? = null
    private var drawerListState: DrawerListState? = null
    lateinit var phoneNav: PhoneNavHostState
        private set

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var drawerLayout: DrawerLayout

    private var snackbar: Snackbar? = null

    /** When true, a successful profile check opens the start route (after Recheck). */
    private var openStartOnProfileSuccess: Boolean = false

    private lateinit var currentProfile: Profile

    /**
     * Lowest-priority back handler: drawer close, then NavHost pop (service EPG, etc.), then
     * optional leave-confirm. Registered early in [onCreate] so Compose [BackHandler] and
     * fragment callbacks (provider drill-down, NavHost) stay higher priority.
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
            if (shouldConfirm && supportFragmentManager.backStackEntryCount == 0) {
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
            navigationHelper!!.setAvailableFeatures()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        super.onCreate(savedInstanceState)
        // Register before fragments/Compose so those BackHandlers outrank leave-confirm.
        onBackPressedDispatcher.addCallback(this, leaveAppCallback)

        isDrawerOpenNotified = false
        currentProfile = Profile.getDefault()
        phoneNav = PhoneNavHostState(this, this)
        if (savedInstanceState != null) {
            phoneNav.restoreState(savedInstanceState)
        } else {
            phoneNav.setStartRoute(StartScreen.navRoute(this))
        }
        initViews()
        bindPhoneNavCompose()
        startSessionReachabilityProbe()
        DreamDroid.setCurrentProfileChangedListener(this)
        PreferenceManager.getDefaultSharedPreferences(
            this
        ).registerOnSharedPreferenceChangeListener(this)
        showChangeLog(true)
        handleSearchIntent(intent)
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
                    val previousSession =
                        SessionConnectionHolder.shared.status.value.session
                    val ran = probeSessionReachabilityIfNeeded(
                        holder = SessionConnectionHolder.shared,
                        hasCache = hasUseDrivenCache(
                            DreamDroid.getCurrentProfile(),
                            this@MainActivity
                        ),
                        isBusy = { checkProfileJob != null },
                        check = {
                            val profile = DreamDroid.getCurrentProfile()
                            profile.cachedDeviceInfo = null
                            withContext(Dispatchers.IO) {
                                CheckProfile.checkProfile(profile, this@MainActivity)
                            }
                        }
                    )
                    if (ran) {
                        bindDrawerConnectionChip()
                        val session = SessionConnectionHolder.shared.status.value.session
                        if (session == ConnectionStatus.Session.Online &&
                            previousSession != ConnectionStatus.Session.Online
                        ) {
                            navigationHelper?.setAvailableFeatures()
                        }
                    }
                    delay(SESSION_REACHABILITY_INTERVAL_MS)
                }
            }
        }
    }

    override fun onLocalNetworkPermissionGranted() {
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

    override fun onSaveInstanceState(outState: Bundle) {
        phoneNav.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun dispatchActivityResultToNavHandle(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        phoneNav.onHostActivityResult(requestCode, resultCode, data)
        return true
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (slider) {
            drawerToggle.syncState()
        }
    }

    override fun onResume() {
        super.onResume()
        checkNavigationHelper(true)
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(
            this
        ).unregisterOnSharedPreferenceChangeListener(this)
        if (DreamDroid.getCurrentProfileChangedListener() === this) {
            DreamDroid.setCurrentProfileChangedListener(null)
        }
        super.onDestroy()
    }

    private fun ensureNavigationHelper() {
        if (navigationHelper != null) {
            return
        }
        // TODO preserve/restore navigationHelper properly
        // Keep DrawerListState across pause/resume so the Compose drawer
        // highlight survives helper recreation (NavigationView used to keep
        // checked state on the view itself).
        if (drawerListState == null) {
            drawerListState = DrawerListState()
        }
        navigationHelper = NavigationHelper(this, drawerListState!!)
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
        val state = drawerListState ?: return
        val itemId = DrawerHighlight.itemIdForRoute(
            route,
            previousRoute
        ) ?: return
        if (itemId == R.id.menu_none) {
            state.clearSelection()
        } else {
            state.select(itemId)
        }
    }

    override fun onPause() {
        navigationHelper?.onDestroy()
        navigationHelper = null
        super.onPause()
    }

    override fun onStop() {
        checkProfileJob?.cancel(null)
        checkProfileJob = null
        SessionConnectionHolder.shared.cancelChecking()
        bindDrawerConnectionChip()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Pass any configuration change to the drawer toggle
        if (slider) {
            drawerToggle.onConfigurationChanged(newConfig)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.search, menu)
        return true
    }

    private fun bindPhoneNavCompose() {
        val container = findViewById<ViewGroup>(R.id.detail_view)
        if (container.findViewById<View>(R.id.phone_nav_compose) != null) {
            return
        }
        val composeView = ComposeView(this).apply {
            id = R.id.phone_nav_compose
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            bindPhoneNavHost(phoneNav)
        }
        container.addView(composeView)
    }

    private fun initViews() {
        setContentView(R.layout.dualpane)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        slider = findViewById<View?>(R.id.drawer_layout) != null
        if (slider) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setHomeButtonEnabled(true)

            drawerLayout = findViewById(R.id.drawer_layout)
            drawerToggle = object : ActionBarDrawerToggle(
                this, /* host Activity */
                drawerLayout, /* DrawerLayout object */
                R.string.drawer_open, /* "open drawer" description for accessibility */
                R.string.drawer_close /* "close drawer" description for accessibility */
            ) {
                override fun onDrawerClosed(view: View) {
                    isDrawerOpenNotified = false
                    supportInvalidateOptionsMenu()
                }

                override fun onDrawerOpened(drawerView: View) {
                    supportInvalidateOptionsMenu()
                    dismissSnackbar()
                }

                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    if (isDrawerOpen || isDrawerOpenNotified) {
                        return
                    }
                    isDrawerOpenNotified = true
                }
            }
            drawerLayout.addDrawerListener(drawerToggle)

            val profileChooser = findViewById<View>(R.id.drawer_profile)
            profileChooser.setOnClickListener {
                checkNavigationHelper()
                navigationHelper!!.navigateTo(R.id.menu_navigation_profiles)
            }
            activeProfile = findViewById(R.id.drawer_profile_name)
            connectionState = findViewById(R.id.drawer_profile_status)
            bindDrawerConnectionChip()
        } else {
            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        }

        if (!this::activeProfile.isInitialized) {
            activeProfile = TextView(this)
        }
        if (!this::connectionState.isInitialized) {
            connectionState = TextView(this)
        }
    }

    override fun setTitle(title: CharSequence?) {
        val titleView = findViewById<TextView?>(R.id.toolbar_title)
        if (titleView != null) {
            titleView.text = title
            super.setTitle("")
        } else {
            super.setTitle(title)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (slider && drawerToggle.onOptionsItemSelected(item)) {
            return true
        }

        when (item.itemId) {
            android.R.id.home -> {
                if (isNavigationDrawerVisible()) {
                    toggle()
                }
            }

            R.id.action_search -> {
                phoneNav.navigateToEpgSearch("")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun isNavigationDrawerVisible(): Boolean {
        if (slider) {
            val navigationView = findViewById<View?>(R.id.navigation_view)
            return navigationView != null && drawerLayout.isDrawerOpen(navigationView)
        }
        return false
    }

    fun toggle() {
        if (slider) {
            val navigationView = findViewById<View?>(R.id.navigation_view)
            if (navigationView != null) {
                if (isNavigationDrawerVisible()) {
                    drawerLayout.closeDrawer(navigationView)
                } else {
                    drawerLayout.openDrawer(navigationView)
                }
            }
        }
    }

    fun showContent() {
        if (slider) {
            drawerLayout.closeDrawers()
        }
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
        activeProfile.text = DreamDroid.getCurrentProfile().name
    }

    private fun bindDrawerConnectionChip() {
        if (!this::connectionState.isInitialized || !this::phoneNav.isInitialized) {
            return
        }
        connectionState.setText(phoneNav.connectionStatusFlow().value.chipLabelRes())
    }

    fun unregisterFab(id: Int) {
        val fab = findViewById<View?>(id) ?: return
        fab.setOnClickListener(null)
        fab.setOnLongClickListener(null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (PreferenceManager.getDefaultSharedPreferences(
                this
            ).getBoolean("volume_control", false)
        ) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    // TODO onVolumeButtonClicked(Volume.CMD_UP);
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    // TODO onVolumeButtonClicked(Volume.CMD_DOWN);
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            super.onKeyUp(keyCode, event)

    override val isMultiPane: Boolean
        get() = true

    override val isDrawerOpen: Boolean
        get() = isNavigationDrawerVisible()

    fun isSlidingMenu(): Boolean = slider

    fun finish(finishFragment: Boolean) {
        if (finishFragment) {
            // Phone destinations live in Compose NavHost; nothing to pop here.
        } else {
            super.finish()
        }
    }

    /*
     * Dialog action routing for Compose choice / progress / connection dialogs.
     * EPG/movie detail sheets are in-composition ModalBottomSheet (Phase 2.1g-ii-d).
     */
    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        phoneNav.composeDialogActionListener?.onDialogAction(action, details, dialogTag)
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

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        Log.w(DreamDroid.LOG_TAG, key ?: "")
        if (DreamDroid.PREFS_KEY_THEME_TYPE == key) {
            DreamDroid.setTheme(this)
            if (!isPaused()) {
                recreate()
            }
        }
    }
}
