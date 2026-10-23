package net.reichholf.dreamdroid.activities

import android.annotation.TargetApi
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStream
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.getSerializableExtraCompat
import net.reichholf.dreamdroid.helpers.isTelevision
import net.reichholf.dreamdroid.tv.ui.allowsStreaming
import net.reichholf.dreamdroid.tv.ui.shouldKeepTvStreamingActivity
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.video.SLEEP_TIMER_EXTEND_MINUTES
import net.reichholf.dreamdroid.ui.video.SleepTimer
import net.reichholf.dreamdroid.ui.video.SleepTimerCountdown
import net.reichholf.dreamdroid.ui.video.VideoOverlayController
import net.reichholf.dreamdroid.ui.video.VideoPlaybackViewModel
import net.reichholf.dreamdroid.ui.video.VideoSnackbarHost
import net.reichholf.dreamdroid.video.PlayRequest
import net.reichholf.dreamdroid.video.PlayerEvent
import net.reichholf.dreamdroid.video.PlayerViews
import net.reichholf.dreamdroid.video.VideoLayout
import net.reichholf.dreamdroid.video.VideoPlayer
import net.reichholf.dreamdroid.video.VlcEngine

/**
 * Created by reichi on 16/02/16.
 */
@AndroidEntryPoint
class VideoActivity :
    ComponentActivity(),
    DialogActionListener {

    /** Holds the engines' video views; the source of the PiP transition. */
    private var playerContainer: View? = null
    var overlay: VideoOverlayController? = null
    private val playbackViewModel: VideoPlaybackViewModel by viewModels()

    @Inject
    lateinit var sessions: SessionConnectionHolder

    @Inject
    lateinit var player: VideoPlayer

    @Inject
    lateinit var settings: SettingsRepository

    /** Views and event listener are on [player] between onStart and onStop. */
    private var playerAttached: Boolean = false

    /** The picture's display size, for the PiP aspect ratio; 0 until the engine reports it. */
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    private val localNetworkPermissionRequest = LocalNetworkPermissionRequest(this)
    private var playbackAlreadyStarted: Boolean = false
    private var tvStreamingRejected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bars sit over video; keep them dark whatever the app theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setFullScreen()
        super.onCreate(savedInstanceState)
        val isTelevision = isTelevision()
        if (!shouldKeepTvStreamingActivity(
                isTelevision,
                sessions.status.value,
                playbackAlreadyStarted
            )
        ) {
            tvStreamingRejected = true
            finish()
            return
        }
        if (isTelevision) {
            observeTvSession()
        }
        localNetworkPermissionRequest.ensure(this)
        setContentView(R.layout.video_player)
        findViewById<ComposeView>(R.id.video_snackbar_host).setContent {
            DreamDroidTheme(forceDark = true) {
                val uiState by playbackViewModel.uiState.collectAsStateWithLifecycle()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    (uiState.sleepTimer as? SleepTimer.Closing)?.let { closing ->
                        SleepTimerCountdown(
                            closing = closing,
                            onExtend = {
                                playbackViewModel.setSleepTimer(SLEEP_TIMER_EXTEND_MINUTES)
                            },
                            onCancel = { playbackViewModel.setSleepTimer(0) }
                        )
                    }
                    VideoSnackbarHost(uiState.userMessage, playbackViewModel::onMessageShown)
                }
            }
        }
        title = ""
        initializeOverlay()
    }

    private fun observeTvSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessions.status.collect { status ->
                    if (!shouldKeepTvStreamingActivity(
                            isTelevision(),
                            status,
                            playbackAlreadyStarted
                        )
                    ) {
                        finish()
                        return@collect
                    }
                    overlay?.setTvStreamingChromeEnabled(status.allowsStreaming())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (tvStreamingRejected) {
            return
        }
        initialize()
    }

    override fun onResume() {
        super.onResume()
        if (tvStreamingRejected) {
            return
        }
        overlay?.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setFullScreen()
    }

    override fun onPause() {
        if (!tvStreamingRejected) {
            overlay?.hideOverlays()
            overlay?.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        if (!tvStreamingRejected) {
            cleanup()
            player.release()
        }
        super.onStop()
    }

    override fun onDestroy() {
        overlay?.detach()
        overlay = null
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    fun handleIntent(intent: Intent) {
        setIntent(intent)
        if (Intent.ACTION_VIEW != intent.action) return
        overlay?.applyPlaybackExtras(intent.extras)
        if (!playerAttached) return
        val data = intent.data ?: return
        val values = settings.current()
        player.playUri(
            PlayRequest(
                url = data.toString(),
                engine = values.engine,
                hwAccelFlags = values.videoHardwareAcceleration.toIntOrNull()
                    ?: VlcEngine.MEDIA_HWACCEL_ENABLED,
                bufferMs = values.streamBuffer,
                // Live services carry their reference; recordings do not.
                live = intent.hasExtra(VideoOverlayController.SERVICE_REFERENCE),
                preferredAudioLanguage = values.preferredAudioLanguage
            )
        )
        playbackAlreadyStarted = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (tvStreamingRejected) {
            return super.onKeyDown(keyCode, event)
        }
        return overlay?.onKeyDown(keyCode) == true ||
            super.onKeyDown(keyCode, event)
    }

    private fun initialize() {
        cleanup()

        playerContainer = findViewById(R.id.player_container)
        player.attach(
            PlayerViews(
                vlcLayout = findViewById(R.id.vlc_video_layout),
                media3Frame = findViewById(R.id.media3_frame),
                media3Surface = findViewById(R.id.media3_surface),
                subtitleView = findViewById(R.id.subtitles_view)
            )
        )
        player.setEventListener(::onPlayerEvent)
        playerAttached = true

        handleIntent(intent)
        setFullScreen()
    }

    private fun initializeOverlay() {
        val controller =
            overlay
                ?: VideoOverlayController(this, playbackViewModel, sessions, player)
                    .also { overlay = it }
        controller.attach(intent.extras)
    }

    private fun cleanup() {
        if (!playerAttached) return
        playerAttached = false
        player.detach()
        player.setEventListener(null)
    }

    /**
     * Saves the live service that plays now as the last channel. [getIntent] is the stream
     * that plays: [handleIntent] sets it for a new intent and for a zap in the overlay.
     */
    private fun rememberLiveStream() {
        val reference = intent.getStringExtra(VideoOverlayController.SERVICE_REFERENCE) ?: return
        val service =
            intent.getSerializableExtraCompat<ServiceNowNext>(VideoOverlayController.SERVICE_INFO)
        playbackViewModel.onLiveStreamPlaying(
            LastLiveStream(
                reference = reference,
                serviceName = service?.serviceName.orEmpty(),
                bouquetRef = intent.getStringExtra(VideoOverlayController.BOUQUET_REFERENCE)
            )
        )
    }

    private fun onMediaPlaying() {
        setPictureInPictureParams(getPipParams())
    }

    private fun getPipParams(): PictureInPictureParams {
        val sourceRectHint = Rect()
        playerContainer?.getGlobalVisibleRect(sourceRectHint)
        val ar = Rational(videoWidth, videoHeight)
        val builder = PictureInPictureParams.Builder()
        if (ar.isFinite && !ar.isZero) {
            builder.setAspectRatio(ar)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            builder.setSourceRectHint(sourceRectHint)
        }
        return builder.build()
    }

    private fun doEnterPip(): Boolean {
        val params = getPipParams()
        try {
            enterPictureInPictureMode(params)
        } catch (e: IllegalArgumentException) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
        return true
    }

    @TargetApi(Build.VERSION_CODES.R)
    override fun onPictureInPictureRequested(): Boolean = doEnterPip()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode) doEnterPip()
    }

    fun setFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun onVideoLayout(layout: VideoLayout) {
        // The engines place the picture themselves; PiP only needs its shape.
        val sar = if (layout.sarNum > 0 && layout.sarDen > 0) {
            layout.sarNum.toDouble() / layout.sarDen
        } else {
            1.0
        }
        videoWidth = (layout.visibleWidth * sar).toInt()
        videoHeight = layout.visibleHeight
        if (playerAttached) {
            // The size arrives after Playing; PiP needs it for the aspect ratio.
            setPictureInPictureParams(getPipParams())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        overlay?.onPictureInPictureModeChanged()
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        overlay?.onDialogAction(action, details, dialogTag)
    }

    override fun finish() {
        super.finish()
        cleanup()
    }

    private fun onPlayerEvent(event: PlayerEvent) {
        if (event == PlayerEvent.Playing) {
            playbackAlreadyStarted = true
            rememberLiveStream()
        }
        // The engine reports the size while views are attached, the overlay or not.
        if (event is PlayerEvent.Layout) {
            onVideoLayout(event.layout)
            return
        }
        val chrome = overlay ?: return
        when (event) {
            PlayerEvent.Playing -> onMediaPlaying()
            PlayerEvent.Ended -> finish()
            else -> Unit
        }
        chrome.onEvent(event)
    }
}
