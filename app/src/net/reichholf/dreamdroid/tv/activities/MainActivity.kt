package net.reichholf.dreamdroid.tv.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.LocalNetworkPermission
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.enigma2.PiconImageLoader
import net.reichholf.dreamdroid.tv.ui.TvComposeHubHost
import net.reichholf.dreamdroid.tv.ui.TvHubViewModel
import net.reichholf.dreamdroid.tv.ui.TvShellViewModel
import net.reichholf.dreamdroid.ui.session.SESSION_REACHABILITY_INTERVAL_MS
import net.reichholf.dreamdroid.ui.setup.SetupAssistantScreen
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme

/**
 * Created by Stephan on 16.10.2016.
 *
 * TV browse host activity (Compose hub via [TvComposeHubHost]). [TvShellViewModel] checks
 * the profile and probes reachability so the hub can show Online / Offline / Checking.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var profiles: ProfileRepository

    private val localNetworkPermissionRequest = LocalNetworkPermissionRequest(this) {
        lanGranted = true
        if (!showingSetup) {
            // The hub ViewModel outlives recreate(), so its loads that failed
            // without the permission have to be restarted by hand.
            hubViewModel.reload()
            recreate()
        }
    }
    private val hubViewModel: TvHubViewModel by viewModels()
    private val shellViewModel: TvShellViewModel by viewModels()
    private var showingSetup: Boolean = false
    private var lanGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        DreamDroid.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        startSessionReachabilityProbe()
        if (!profiles.hasCurrent()) {
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
        if (!profiles.ensureCurrent()) {
            shellViewModel.cancelCheck()
            showSetup()
        }
    }

    private fun showSetup() {
        showingSetup = true
        lanGranted = LocalNetworkPermission.isGranted(this)
        setContent {
            DreamDroidTvTheme {
                SetupAssistantScreen(
                    viewModel = hiltViewModel(),
                    localNetworkGranted = lanGranted,
                    onRequestLocalNetwork = { localNetworkPermissionRequest.ensure(this) },
                    onFinished = { startHub() },
                    onLeave = { finish() }
                )
            }
        }
    }

    private fun startHub() {
        showingSetup = false
        localNetworkPermissionRequest.ensure(this)
        shellViewModel.start()
        TvComposeHubHost.install(this, shellViewModel::recheck)
        try {
            // Coil ImageLoader w/ OkHttpClient. Trust-all is per OkHttp client.
            // Do not flip process-wide HttpsURLConnection follow-redirects.
            PiconImageLoader.install(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** While resumed, probe the receiver every 30s and right away on resume. */
    private fun startSessionReachabilityProbe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (!showingSetup) {
                        shellViewModel.probeReachability()
                    }
                    delay(SESSION_REACHABILITY_INTERVAL_MS)
                }
            }
        }
    }
}
