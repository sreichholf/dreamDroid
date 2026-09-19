package net.reichholf.dreamdroid.activities

import android.annotation.TargetApi
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.MenuItem
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.VideoOverlayFragment
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.tv.ui.allowsStreaming
import net.reichholf.dreamdroid.tv.ui.shouldKeepTvStreamingActivity
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.video.VLCPlayer
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout

/**
 * Created by reichi on 16/02/16.
 */
class VideoActivity :
    AppCompatActivity(),
    IVLCVout.OnNewVideoLayoutListener,
    IVLCVout.Callback,
    DialogActionListener,
    MediaPlayer.EventListener {

    var surfaceFrame: FrameLayout? = null
    var surfaceView: SurfaceView? = null
    lateinit var subtitlesSurfaceView: SurfaceView
    var player: VLCPlayer? = null
    var overlayFragment: VideoOverlayFragment? = null

    var onLayoutChangeListener: View.OnLayoutChangeListener? = null

    var currentScreenOrientation: Int = 0

    var videoWidth: Int = 0
    var videoHeight: Int = 0
    var videoVisibleWidth: Int = 0
    var videoVisibleHeight: Int = 0
    var sarNum: Int = 0
    var sarDen: Int = 0

    private val handler = Handler(Looper.getMainLooper())
    private val localNetworkPermissionRequest = LocalNetworkPermissionRequest(this)
    private var playbackAlreadyStarted: Boolean = false
    private var tvStreamingRejected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        enableEdgeToEdge()
        setFullScreen()
        super.onCreate(savedInstanceState)
        val isTelevision = isTelevisionDevice()
        if (!shouldKeepTvStreamingActivity(
                isTelevision,
                SessionConnectionHolder.shared.status.value,
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
        surfaceFrameAddLayoutListener(true)
        currentScreenOrientation = resources.configuration.orientation
        title = ""
        initializeOverlay()
    }

    private fun isTelevisionDevice(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION

    private fun observeTvSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionConnectionHolder.shared.status.collect { status ->
                    if (!shouldKeepTvStreamingActivity(
                            isTelevisionDevice(),
                            status,
                            playbackAlreadyStarted
                        )
                    ) {
                        finish()
                        return@collect
                    }
                    overlayFragment?.setTvStreamingChromeEnabled(status.allowsStreaming())
                }
            }
        }
    }

    private fun surfaceFrameAddLayoutListener(add: Boolean) {
        if (surfaceFrame == null || add == (onLayoutChangeListener != null)) return
        if (add) {
            onLayoutChangeListener =
                object : View.OnLayoutChangeListener {
                    private val runnable = Runnable { changeSurfaceLayout() }

                    override fun onLayoutChange(
                        v: View,
                        left: Int,
                        top: Int,
                        right: Int,
                        bottom: Int,
                        oldLeft: Int,
                        oldTop: Int,
                        oldRight: Int,
                        oldBottom: Int
                    ) {
                        if (left != oldLeft || top != oldTop || right != oldRight ||
                            bottom != oldBottom
                        ) {
                            /* changeSurfaceLayout need to be called after the layout changed */
                            handler.removeCallbacks(runnable)
                            handler.post(runnable)
                        }
                    }
                }
            surfaceFrame!!.addOnLayoutChangeListener(onLayoutChangeListener)
            changeSurfaceLayout()
        } else {
            surfaceFrame!!.removeOnLayoutChangeListener(onLayoutChangeListener)
            onLayoutChangeListener = null
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
        overlayFragment?.showOverlays()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setFullScreen()
    }

    override fun onPause() {
        if (!tvStreamingRejected) {
            overlayFragment?.hideOverlays()
        }
        super.onPause()
    }

    override fun onStop() {
        if (!tvStreamingRejected) {
            cleanup()
            VLCPlayer.release()
            surfaceFrameAddLayoutListener(false)
        }
        super.onStop()
    }

    override fun onDestroy() {
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentScreenOrientation = newConfig.orientation
        changeSurfaceLayout()
    }

    fun handleIntent(intent: Intent) {
        setIntent(intent)
        if (Intent.ACTION_VIEW != intent.action) return
        overlayFragment?.applyPlaybackExtras(intent.extras)
        val player = this.player ?: return
        val data = intent.data ?: return
        val accel =
            Integer.parseInt(
                PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getString(
                        DreamDroid.PREFS_KEY_HWACCEL,
                        Integer.toString(VLCPlayer.MEDIA_HWACCEL_ENABLED)
                    )
            )
        player.playUri(data, accel)
        playbackAlreadyStarted = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (tvStreamingRejected) {
            return super.onKeyDown(keyCode, event)
        }
        return overlayFragment?.onKeyDown(keyCode, event) == true ||
            super.onKeyDown(keyCode, event)
    }

    private fun initialize() {
        cleanup()
        player = VLCPlayer.get()

        surfaceFrame = findViewById(R.id.player_surface_frame)
        surfaceView = findViewById(R.id.player_surface)
        subtitlesSurfaceView = findViewById(R.id.subtitles_surface)
        subtitlesSurfaceView.setZOrderMediaOverlay(true)
        subtitlesSurfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)

        player!!.attach(this, surfaceView, subtitlesSurfaceView)

        VLCPlayer.getMediaPlayer()!!.vlcVout.addCallback(this)
        VLCPlayer.getMediaPlayer()!!.setEventListener(this)

        handleIntent(intent)
        setFullScreen()
    }

    private fun initializeOverlay() {
        if (overlayFragment == null) {
            overlayFragment =
                supportFragmentManager.findFragmentByTag("video_overlay_fragment")
                    as VideoOverlayFragment?
        }
        val existing = overlayFragment
        if (existing != null) {
            existing.applyPlaybackExtras(intent.extras)
            return
        }

        val overlay = VideoOverlayFragment()
        overlay.arguments = intent.extras
        overlayFragment = overlay
        supportFragmentManager.commit {
            replace(R.id.overlay, overlay, "video_overlay_fragment")
        }
    }

    private fun cleanup() {
        cleanup(false)
    }

    private fun cleanup(force: Boolean) {
        if (player == null && force) player = VLCPlayer.get()
        if (player == null) return
        player!!.detach()
        player = null
        surfaceView = null
        VLCPlayer.getMediaPlayer()!!.vlcVout.removeCallback(this)
        VLCPlayer.getMediaPlayer()!!.setEventListener(null)
    }

    protected fun onMediaPlaying() {
        if (videoWidth * videoHeight == 0) {
            videoHeight = player!!.getVideoHeight()
            videoWidth = player!!.getVideoWidth()
            videoVisibleWidth = videoWidth
            videoVisibleHeight = videoHeight
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(getPipParams())
        }
    }

    protected fun changeSurfaceLayout() {
        if (player == null) return
        var sw: Int
        var sh: Int

        // get screen size
        sw = window.decorView.width
        sh = window.decorView.height

        // DecorView size ignores orientation sometimes; swap width/height if needed.
        val isPortrait = currentScreenOrientation == Configuration.ORIENTATION_PORTRAIT

        if ((sw > sh && isPortrait) || (sw < sh && !isPortrait)) {
            val w = sw
            sw = sh
            sh = w
        }

        // sanity check
        if (sw * sh == 0) {
            Log.e(TAG, "Invalid surface size")
            return
        }
        val player = VLCPlayer.getMediaPlayer()
        if (player != null) {
            val vlcVout = player.vlcVout
            vlcVout.setWindowSize(sw, sh)
        }

        val surface = surfaceView!!
        val subtitlesSurface = subtitlesSurfaceView
        val surfaceFrame = this.surfaceFrame!!
        var lp = surface.layoutParams

        if (videoWidth * videoHeight == 0) {
            videoHeight = this.player!!.getVideoHeight()
            videoWidth = this.player!!.getVideoWidth()
            videoVisibleWidth = videoWidth
            videoVisibleHeight = videoHeight
        }

        if (videoWidth * videoHeight == 0 || isInPictureInPictureMode) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width = LayoutParams.MATCH_PARENT
            lp.height = LayoutParams.MATCH_PARENT
            surface.layoutParams = lp
            lp = surfaceFrame.layoutParams
            lp.width = LayoutParams.MATCH_PARENT
            lp.height = LayoutParams.MATCH_PARENT
            surfaceFrame.layoutParams = lp
            if (player != null && videoWidth * videoHeight == 0) {
                player.setAspectRatio(null)
                player.setScale(0f)
            }
            return
        }

        if (player != null && lp.width == lp.height && lp.width == LayoutParams.MATCH_PARENT) {
            /* We handle the placement of the video using Android View LayoutParams */
            player.setAspectRatio(null)
            player.setScale(0f)
        }

        // compute the aspect ratio
        val ar: Double
        val vw: Double
        if (sarDen == sarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = videoVisibleWidth.toDouble()
            ar = videoVisibleWidth.toDouble() / videoVisibleHeight.toDouble()
        } else {
            /* Use the specified aspect ratio */
            vw = videoVisibleWidth * sarNum.toDouble() / sarDen
            ar = vw / videoVisibleHeight
        }

        var dw = sw.toDouble()
        var dh = sh.toDouble()

        // compute the display aspect ratio
        val dar = dw / dh
        if (dar < ar) {
            dh = dw / ar
        } else {
            dw = dh * ar
        }

        // set display size
        lp.width = ceil(dw * videoWidth / videoVisibleWidth).toInt()
        lp.height = ceil(dh * videoHeight / videoVisibleHeight).toInt()
        surface.layoutParams = lp
        subtitlesSurface.layoutParams = lp

        // set frame size (crop if necessary)
        lp = surfaceFrame.layoutParams
        lp.width = floor(dw).toInt()
        lp.height = floor(dh).toInt()
        surfaceFrame.layoutParams = lp

        surface.invalidate()
        subtitlesSurface.invalidate()
    }

    protected fun getPipParams(): PictureInPictureParams {
        val sourceRectHint = Rect()
        surfaceView!!.getGlobalVisibleRect(sourceRectHint)
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

    @TargetApi(Build.VERSION_CODES.O)
    protected fun doEnterPip(): Boolean {
        val params = getPipParams()
        try {
            enterPictureInPictureMode(params)
        } catch (e: IllegalArgumentException) {
            enterPictureInPictureMode()
        }
        return true
    }

    @TargetApi(Build.VERSION_CODES.R)
    override fun onPictureInPictureRequested(): Boolean = doEnterPip()

    @TargetApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        if (!isInPictureInPictureMode) doEnterPip()
    }

    @Suppress("DEPRECATION")
    fun setFullScreen() {
        var visibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
        var navigation = View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            navigation = navigation or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        visibility = visibility or navigation
        window.decorView.systemUiVisibility = visibility
    }

    override fun onNewVideoLayout(
        vlcVout: IVLCVout,
        width: Int,
        height: Int,
        visibleWidth: Int,
        visibleHeight: Int,
        sarNum: Int,
        sarDen: Int
    ) {
        videoWidth = width
        videoHeight = height
        videoVisibleWidth = visibleWidth
        videoVisibleHeight = visibleHeight
        this.sarNum = sarNum
        this.sarDen = sarDen
        changeSurfaceLayout()
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout) {
        val mediaPlayer = VLCPlayer.getMediaPlayer()!!
        mediaPlayer.setAspectRatio(null)
        mediaPlayer.setScale(0f)
        mediaPlayer.setVideoTrackEnabled(true)
    }

    override fun isInPictureInPictureMode(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && super.isInPictureInPictureMode()

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        changeSurfaceLayout()
    }

    override fun onSurfacesDestroyed(vlcVout: IVLCVout) {}

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        if (overlayFragment == null) return
        overlayFragment!!.onDialogAction(action, details, dialogTag)
    }

    override fun finish() {
        super.finish()
        cleanup()
    }

    override fun onEvent(event: MediaPlayer.Event) {
        if (event.type == MediaPlayer.Event.Playing) {
            playbackAlreadyStarted = true
        }
        val overlay = overlayFragment ?: return
        overlay.onUpdateButtons()
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                onMediaPlaying()
                // Fall through from Playing into ESSelected (matches Java switch).
                if (event.esChangedType == IMedia.Track.Type.Video) {
                    changeSurfaceLayout()
                }
            }

            MediaPlayer.Event.ESSelected -> {
                if (event.esChangedType == IMedia.Track.Type.Video) {
                    changeSurfaceLayout()
                }
            }

            MediaPlayer.Event.EndReached -> finish()
        }
        overlay.onEvent(event)
    }

    companion object {
        val TAG: String = VideoActivity::class.java.simpleName
    }
}
