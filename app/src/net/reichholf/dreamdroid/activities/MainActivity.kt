/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities

import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
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
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.ProfileChangedListener
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.BaseActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.launchCheckProfileLoad
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler
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

/**
 * @author sre
 */
class MainActivity :
    BaseActivity(),
    MultiPaneHandler,
    ProfileChangedListener,
    DialogActionListener,
    SearchView.OnQueryTextListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    DrawerRouteHighlighter {

    private var mSlider: Boolean = false
    private var mIsDrawerOpen: Boolean = false
    private lateinit var mActiveProfile: TextView
    private lateinit var mConnectionState: TextView

    private var mCheckProfileJob: Job? = null

    private var mNavigationHelper: NavigationHelper? = null
    private var mDrawerListState: DrawerListState? = null
    private var mDetailFragment: Fragment? = null
    lateinit var phoneNav: PhoneNavHostState
        private set

    private lateinit var mDrawerToggle: ActionBarDrawerToggle
    private lateinit var mDrawerLayout: DrawerLayout

    private var mSnackbar: Snackbar? = null

    /** When true, a successful profile check opens the start route (after Recheck). */
    private var mOpenStartOnProfileSuccess: Boolean = false

    private lateinit var mCurrentProfile: Profile

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
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.leave_confirm)
                    .setMessage(R.string.leave_confirm_long)
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                finish()
            }
        }
    }

    private fun dismissSnackbar() {
        mSnackbar?.dismiss()
        mSnackbar = null
    }

    private fun showProfileCheckChecking(message: String) {
        dismissSnackbar()
        val ui = ProfileCheckUi.Checking(message)
        phoneNav.navigateToProfileCheck(ui)
    }

    private fun showProfileCheckFailed(result: ProfileCheckResult) {
        dismissSnackbar()
        mOpenStartOnProfileSuccess = true
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
        // Keep mOpenStartOnProfileSuccess so a later success opens the start route.
        showProfileCheckChecking(getString(R.string.checking_connection))
        val p = DreamDroid.getCurrentProfile()
        p.cachedDeviceInfo = null
        onProfileChanged(p, true)
    }

    fun openProfilesFromProfileCheckFailed() {
        mOpenStartOnProfileSuccess = false
        if (phoneNav.isOnProfileCheckRoute()) {
            // Keep the gate under Profiles so Back returns to the check.
            phoneNav.navigateAboveProfileCheck(PhoneNavRoutes.PROFILES)
            return
        }
        mNavigationHelper?.navigateTo(R.id.menu_navigation_profiles)
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
            mNavigationHelper!!.navigateTo(R.id.menu_navigation_profiles)
        } else {
            mNavigationHelper!!.navigateTo(StartScreen.menuId(this))
        }
    }

    private fun isPaused(): Boolean = !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun getProfileCheckContext(): Context = this

    private fun onProfileCheckProgress(state: String) {
        setConnectionState(state, false)
        updateProfileCheckChecking(state)
    }

    fun onProfileChecked(result: ProfileCheckResult) {
        if (isPaused() || checkNavigationHelper()) {
            return
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstStart = sp.getBoolean(DreamDroid.PREFS_KEY_FIRST_START, true)

        if (result.hasError && !result.isSoftError) {
            val error = getString(result.errorTextId)
            setConnectionState(error, true)
            showProfileCheckFailed(result)
        } else {
            dismissSnackbar()
            if (result.isSoftError) {
                val error = getString(result.errorTextId)
                setConnectionState(error, true)
            } else {
                setConnectionState(getString(R.string.ok), true)
            }
            mNavigationHelper!!.setAvailableFeatures()
            val openStart = mOpenStartOnProfileSuccess
            mOpenStartOnProfileSuccess = false
            val onGate = phoneNav.isOnProfileCheckRoute()
            if (onGate || openStart) {
                // Leave PROFILE_CHECK on the back stack so Back returns to the gate.
                leaveProfileCheckGate(isFirstStart)
            } else if (isFirstStart) {
                mNavigationHelper!!.navigateTo(R.id.menu_navigation_profiles)
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

        mIsDrawerOpen = false
        mCurrentProfile = Profile.getDefault()
        phoneNav = PhoneNavHostState(this, this)
        if (savedInstanceState != null) {
            phoneNav.restoreState(savedInstanceState)
        } else {
            phoneNav.setStartRoute(StartScreen.navRoute(this))
        }
        initViews()
        bindPhoneNavCompose()
        DreamDroid.setCurrentProfileChangedListener(this)
        PreferenceManager.getDefaultSharedPreferences(
            this
        ).registerOnSharedPreferenceChangeListener(this)
        showChangeLog(true)
        handleSearchIntent(intent)
    }

    override fun onLocalNetworkPermissionGranted() {
        onProfileChanged(DreamDroid.getCurrentProfile(), true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSearchIntent(intent)
    }

    /** System / SearchView ACTION_SEARCH — same path as the toolbar query submit. */
    private fun handleSearchIntent(intent: Intent?) {
        if (intent == null || Intent.ACTION_SEARCH != intent.action) {
            return
        }
        val query = intent.getStringExtra(SearchManager.QUERY)
        if (query != null) {
            onQueryTextSubmit(query)
        }
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
        if (mSlider) {
            mDrawerToggle.syncState()
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
        super.onDestroy()
    }

    private fun checkNavigationHelper(): Boolean = checkNavigationHelper(false)

    private fun checkNavigationHelper(isResume: Boolean): Boolean {
        if (mNavigationHelper == null) {
            // TODO preserve/restore mNavigationHelper properly
            // Keep DrawerListState across pause/resume so the Compose drawer
            // highlight survives helper recreation (NavigationView used to keep
            // checked state on the view itself).
            if (mDrawerListState == null) {
                mDrawerListState = DrawerListState()
            }
            mNavigationHelper = NavigationHelper(this, mDrawerListState!!)
            onProfileChanged(DreamDroid.getCurrentProfile(), isResume)
            return true
        }
        return false
    }

    override fun highlightDrawerForRoute(route: String?, previousRoute: String?) {
        val state = mDrawerListState ?: return
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
        mNavigationHelper?.onDestroy()
        mNavigationHelper = null
        super.onPause()
    }

    override fun onStop() {
        mCheckProfileJob?.cancel(null)
        mCheckProfileJob = null
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Pass any configuration change to the drawer toggle
        if (mSlider) {
            mDrawerToggle.onConfigurationChanged(newConfig)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.search, menu)

        // Get the SearchView and set the searchable configuration
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.setIconifiedByDefault(false)
        @Suppress("SENSELESS_COMPARISON")
        if (searchView == null) { // WAIT, WHAT?
            Log.w(TAG, "This is just wrong, there is no searchView?!")
            return true
        }
        searchView.queryHint = getString(R.string.epg_search_hint)
        searchView.setOnQueryTextListener(this)

        return true
    }

    private fun getCurrentDetailFragment(): Fragment? {
        if (mDetailFragment == null) {
            mDetailFragment = supportFragmentManager.findFragmentById(R.id.detail_view)
        }
        return mDetailFragment
    }

    /**
     * Detail pane content for leftover fragment callbacks. Phone destinations live in
     * the activity-owned Compose [PhoneNavHostState], not a Fragment.
     */
    private fun getDetailContentFragment(): Fragment? = getCurrentDetailFragment()

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

        mSlider = findViewById<View?>(R.id.drawer_layout) != null
        if (mSlider) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setHomeButtonEnabled(true)

            mDrawerLayout = findViewById(R.id.drawer_layout)
            mDrawerToggle = object : ActionBarDrawerToggle(
                this, /* host Activity */
                mDrawerLayout, /* DrawerLayout object */
                R.string.drawer_open, /* "open drawer" description for accessibility */
                R.string.drawer_close /* "close drawer" description for accessibility */
            ) {
                override fun onDrawerClosed(view: View) {
                    mIsDrawerOpen = false
                    supportInvalidateOptionsMenu()
                    val callbackHandler = getCurrentDetailFragment() as ActivityCallbackHandler?
                    callbackHandler?.onDrawerClosed()
                }

                override fun onDrawerOpened(drawerView: View) {
                    supportInvalidateOptionsMenu()
                    dismissSnackbar()
                }

                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    if (isDrawerOpen || mIsDrawerOpen) {
                        return
                    }
                    mIsDrawerOpen = true
                    val callbackHandler = getCurrentDetailFragment() as ActivityCallbackHandler?
                    callbackHandler?.onDrawerOpened()
                }
            }
            mDrawerLayout.addDrawerListener(mDrawerToggle)

            val profileChooser = findViewById<View>(R.id.drawer_profile)
            profileChooser.setOnClickListener {
                checkNavigationHelper()
                mNavigationHelper!!.navigateTo(R.id.menu_navigation_profiles)
            }
            mActiveProfile = findViewById(R.id.drawer_profile_name)
            mConnectionState = findViewById(R.id.drawer_profile_status)
        } else {
            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        }

        if (!this::mActiveProfile.isInitialized) {
            mActiveProfile = TextView(this)
        }
        if (!this::mConnectionState.isInitialized) {
            mConnectionState = TextView(this)
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

    private fun showFragment(ft: FragmentTransaction, viewId: Int, fragment: Fragment) {
        if (fragment.isAdded) {
            Log.i(TAG, "Fragment ${(fragment as Any).javaClass.simpleName} already added, showing")
            if (mDetailFragment != null && !fragment.isVisible) {
                ft.hide(mDetailFragment!!)
            }
            ft.show(fragment)
        } else {
            Log.i(TAG, "Fragment ${(fragment as Any).javaClass.simpleName} not added, adding")
            ft.replace(viewId, fragment, (fragment as Any).javaClass.simpleName)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mSlider && mDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }

        when (item.itemId) {
            android.R.id.home -> {
                if (isNavigationDrawerVisible()) {
                    toggle()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun isNavigationDrawerVisible(): Boolean {
        if (mSlider) {
            val navigationView = findViewById<View?>(R.id.navigation_view)
            return navigationView != null && mDrawerLayout.isDrawerOpen(navigationView)
        }
        return false
    }

    fun toggle() {
        if (mSlider) {
            val navigationView = findViewById<View?>(R.id.navigation_view)
            if (navigationView != null) {
                if (isNavigationDrawerVisible()) {
                    mDrawerLayout.closeDrawer(navigationView)
                } else {
                    mDrawerLayout.openDrawer(navigationView)
                }
            }
        }
    }

    fun showContent() {
        if (mSlider) {
            mDrawerLayout.closeDrawers()
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

        setProfileName()
        if (p.cachedDeviceInfo == null) {
            if (p == mCurrentProfile && mCheckProfileJob != null) {
                return
            }
            mCurrentProfile = p
            mCheckProfileJob?.cancel(null)
            mCheckProfileJob = null
            showProfileCheckChecking(getString(R.string.checking_connection))
            mCheckProfileJob = launchCheckProfileLoad(
                p,
                getProfileCheckContext(),
                { state -> onProfileCheckProgress(state) },
                { result ->
                    mCheckProfileJob = null
                    if (result != null) {
                        onProfileChecked(result)
                    }
                }
            )
        } else {
            onProfileChecked(CheckProfile.checkProfile(p, this))
        }
        mNavigationHelper?.onProfileChanged()
    }

    /**
     *
     */
    fun setProfileName() {
        mActiveProfile.text = DreamDroid.getCurrentProfile().name
    }

    /**
     * @param state String representing the current connection state
     */
    private fun setConnectionState(state: String, finished: Boolean) {
        mConnectionState.text = state
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android
     * .support.v4.app.Fragment)
     */
    override fun showDetails(fragment: Fragment) {
        showDetails(fragment, false)
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android
     * .support.v4.app.Fragment, boolean)
     */
    override fun showDetails(fragment: Fragment, addToBackStack: Boolean) {
        if (fragment.isVisible) {
            return
        }
        val ft = supportFragmentManager.beginTransaction()
        if (mDetailFragment != null &&
            mDetailFragment!!.isVisible &&
            PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS,
                true
            )
        ) {
            ft.setCustomAnimations(
                R.animator.activity_open_translate,
                R.animator.activity_close_scale,
                R.animator.activity_open_scale,
                R.animator.activity_close_translate
            )
        }

        val appBarLayout = findViewById<AppBarLayout?>(R.id.appbar)
        appBarLayout?.setExpanded(true, true)
        showFragment(ft, R.id.detail_view, fragment)
        if (addToBackStack) {
            ft.addToBackStack(null)
        }
        ft.commit()
    }

    fun unregisterFab(id: Int) {
        val fab = findViewById<FloatingActionButton?>(id) ?: return
        fab.setOnClickListener(null)
        fab.setOnLongClickListener(null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val callbackHandler = getDetailContentFragment() as ActivityCallbackHandler?
        if (callbackHandler != null) {
            if (callbackHandler.onKeyDown(keyCode, event)) {
                return true
            }
        }

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

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val callbackHandler = getDetailContentFragment() as ActivityCallbackHandler?
        if (callbackHandler != null) {
            if (callbackHandler.onKeyUp(keyCode, event)) {
                return true
            }
        }

        return keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            super.onKeyUp(keyCode, event)
    }

    override val isMultiPane: Boolean
        get() = true

    override val isDrawerOpen: Boolean
        get() = isNavigationDrawerVisible()

    fun isSlidingMenu(): Boolean = mSlider

    fun finish(finishFragment: Boolean) {
        if (finishFragment) {
            // TODO finish() for Fragment
            // getSupportFragmentManager().popBackStackImmediate();
        } else {
            super.finish()
        }
    }

    override fun onFragmentResume(fragment: Fragment) {
        if (fragment != mDetailFragment) {
            mDetailFragment = fragment
            showDetails(fragment)
        }
    }

    override fun onFragmentPause(fragment: Fragment) {
        if (fragment == mDetailFragment) {
            mDetailFragment = null
        }
    }

    override fun showDialogFragment(
        fragmentClass: Class<out DialogFragment>,
        args: Bundle?,
        tag: String
    ) {
        try {
            @Suppress("DEPRECATION")
            val f = fragmentClass.newInstance()
            f.arguments = args
            showDialogFragment(f, tag)
        } catch (e: InstantiationException) {
            Log.e(TAG, e.message ?: "")
        } catch (e: IllegalAccessException) {
            Log.e(TAG, e.message ?: "")
        }
    }

    override fun showDialogFragment(fragment: DialogFragment, tag: String) {
        val fm = supportFragmentManager
        fragment.show(fm, tag)
    }

    /*
     * Dialog action routing for remaining DialogFragments (choice / progress / connection).
     * EPG/movie detail sheets are in-composition ModalBottomSheet (Phase 2.1g-ii-d).
     */
    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        val listener = phoneNav.composeDialogActionListener
        if (listener != null) {
            listener.onDialogAction(action, details, dialogTag)
            super.onDialogAction(action, details, dialogTag)
            return
        }
        getCurrentDetailFragment()
        if (mDetailFragment != null) {
            val content = getDetailContentFragment()
            if (content is DialogActionListener) {
                content.onDialogAction(action, details, dialogTag)
            }
        }
        super.onDialogAction(action, details, dialogTag)
    }

    fun onSetSleepTimer(time: String, action: String, enabled: Boolean) {
        mNavigationHelper?.onSetSleepTimer(time, action, enabled)
    }

    fun onDrawerPowerChoice(action: Int) {
        mNavigationHelper?.onDialogAction(action, null, null)
    }

    fun onSendMessage(text: String, type: String, timeout: String) {
        mNavigationHelper?.onSendMessage(text, type, timeout)
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

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v7.widget.SearchView.OnQueryTextListener#onQueryTextSubmit
     * (java.lang.String)
     */
    override fun onQueryTextSubmit(query: String?): Boolean {
        if (query.isNullOrEmpty()) {
            return true
        }
        phoneNav.navigateToEpgSearch(query)
        return true
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * android.support.v7.widget.SearchView.OnQueryTextListener#onQueryTextChange
     * (java.lang.String)
     */
    override fun onQueryTextChange(newText: String?): Boolean = false

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
    }
}
