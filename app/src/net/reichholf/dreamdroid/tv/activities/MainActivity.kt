package net.reichholf.dreamdroid.tv.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.ProfileChangedListener
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.launchCheckProfileLoad
import net.reichholf.dreamdroid.helpers.LocalNetworkPermission
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile
import net.reichholf.dreamdroid.helpers.enigma2.PiconImageLoader
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.tv.ui.TvComposeHubHost
import net.reichholf.dreamdroid.tv.ui.TvHubViewModel
import net.reichholf.dreamdroid.ui.session.SESSION_REACHABILITY_INTERVAL_MS
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded
import net.reichholf.dreamdroid.ui.setup.SetupAssistantScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme

/**
 * Created by Stephan on 16.10.2016.
 *
 * Kotlin port of the TV browse host activity (Compose hub via [TvComposeHubHost]).
 * Owns CheckProfile + the 30s reachability probe so the hub can show Online /
 * Offline / Checking from [SessionConnectionHolder].
 */
class MainActivity :
    AppCompatActivity(),
    ProfileChangedListener {
    private val localNetworkPermissionRequest = LocalNetworkPermissionRequest(this) {
        lanGranted = true
        if (!showingSetup) {
            // The hub ViewModel outlives recreate(), so its loads that failed
            // without the permission have to be restarted by hand.
            hubViewModel.reload()
            recreate()
        }
    }
    private val hubViewModel: TvHubViewModel by viewModels { TvHubViewModel.Factory }
    private var checkProfileJob: Job? = null
    private var reachabilityJob: Job? = null
    private var showingSetup: Boolean = false
    private var lanGranted by mutableStateOf(false)
    private var currentProfile: Profile = Profile.getDefault()

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!DreamDroid.hasCurrentProfile()) {
            showSetup()
            return
        }
        startHub()
    }

    override fun onStart() {
        super.onStart()
        if (showingSetup) {
            return
        }
        if (!DreamDroid.ensureCurrentProfile(this)) {
            reachabilityJob?.cancel()
            reachabilityJob = null
            checkProfileJob?.cancel()
            checkProfileJob = null
            showSetup()
        }
    }

    private fun showSetup() {
        showingSetup = true
        lanGranted = LocalNetworkPermission.isGranted(this)
        setContent {
            DreamDroidTvTheme {
                SetupAssistantScreen(
                    viewModel = viewModel(),
                    localNetworkGranted = lanGranted,
                    onRequestLocalNetwork = { localNetworkPermissionRequest.ensure(this) },
                    onSave = { profile ->
                        val id = AppDatabase.profilesBlocking(this).addProfile(profile).toInt()
                        profile.id = id
                        DreamDroid.setCurrentProfile(this, id, true)
                        startHub()
                    },
                    onLeave = { finish() }
                )
            }
        }
    }

    private fun startHub() {
        showingSetup = false
        localNetworkPermissionRequest.ensure(this)
        DreamDroid.setCurrentProfileChangedListener(this)
        startSessionReachabilityProbe()
        onProfileChanged(DreamDroid.getCurrentProfile())
        TvComposeHubHost.install(this)
        try {
            HttpsURLConnection.setFollowRedirects(false)
            // Coil ImageLoader w/ OkHttpClient. Do not mutate process-wide
            // HttpsURLConnection defaults; trust-all is per OkHttp client.
            PiconImageLoader.install(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
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
                    probeSessionReachabilityIfNeeded(
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
                    delay(SESSION_REACHABILITY_INTERVAL_MS)
                }
            }
        }
    }

    /** Hub / ProfileCheck Recheck. Clears device-info and re-runs CheckProfile. */
    fun recheckProfile() {
        val profile = DreamDroid.getCurrentProfile()
        profile.cachedDeviceInfo = null
        SessionConnectionHolder.shared.beginChecking()
        startCheckProfile(profile)
    }

    override fun onProfileChanged(p: Profile) {
        if (p.id != currentProfile.id) {
            SessionConnectionHolder.shared.resetForProfileChange()
        }
        if (p.cachedDeviceInfo == null) {
            if (p == currentProfile && checkProfileJob != null) {
                return
            }
            currentProfile = p
            startCheckProfile(p)
        } else {
            currentProfile = p
            onProfileChecked(CheckProfile.checkProfile(p, this))
        }
    }

    private fun startCheckProfile(profile: Profile) {
        checkProfileJob?.cancel(null)
        checkProfileJob = null
        SessionConnectionHolder.shared.beginChecking()
        checkProfileJob = launchCheckProfileLoad(
            profile,
            this,
            { SessionConnectionHolder.shared.beginChecking() },
            { result ->
                checkProfileJob = null
                if (result != null) {
                    onProfileChecked(result)
                }
            }
        )
    }

    private fun onProfileChecked(result: ProfileCheckResult) {
        SessionConnectionHolder.shared.applyProfileCheckResult(
            result,
            hasUseDrivenCache(DreamDroid.getCurrentProfile(), this)
        )
    }

    override fun onDestroy() {
        if (DreamDroid.getCurrentProfileChangedListener() === this) {
            DreamDroid.setCurrentProfileChangedListener(null)
        }
        super.onDestroy()
    }
}
