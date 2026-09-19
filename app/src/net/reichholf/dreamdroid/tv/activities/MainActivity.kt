package net.reichholf.dreamdroid.tv.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile
import net.reichholf.dreamdroid.helpers.enigma2.PiconImageLoader
import net.reichholf.dreamdroid.tv.ui.TvComposeHubHost
import net.reichholf.dreamdroid.ui.session.SESSION_REACHABILITY_INTERVAL_MS
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import net.reichholf.dreamdroid.ui.session.probeSessionReachabilityIfNeeded

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
        recreate()
    }
    private var checkProfileJob: Job? = null
    private var currentProfile: Profile = Profile.getDefault()

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        localNetworkPermissionRequest.ensure(this)
        DreamDroid.setCurrentProfileChangedListener(this)
        startSessionReachabilityProbe()
        onProfileChanged(DreamDroid.getCurrentProfile())
        // Phase 3.1c-iv-f: Compose hub is the TV browse host (Leanback browse removed).
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    probeSessionReachabilityIfNeeded(
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
                    delay(SESSION_REACHABILITY_INTERVAL_MS)
                }
            }
        }
    }

    /** Hub Recheck / ProfileCheck Recheck. Clears the device-info cache and re-runs CheckProfile. */
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
