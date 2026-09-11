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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.VideoOverlayFragment
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.video.VLCPlayer
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Created by reichi on 16/02/16.
 */
class VideoActivity :
    AppCompatActivity(),
    IVLCVout.OnNewVideoLayoutListener,
    IVLCVout.Callback,
    ActionDialog.DialogActionListener,
    MediaPlayer.EventListener {

    var mSurfaceFrame: FrameLayout? = null
    var mSurfaceView: SurfaceView? = null
    lateinit var mSubtitlesSurfaceView: SurfaceView
    var mPlayer: VLCPlayer? = null
    var mOverlayFragment: VideoOverlayFragment? = null

    var mOnLayoutChangeListener: View.OnLayoutChangeListener? = null

    var mCurrentScreenOrientation: Int = 0

    var mVideoWidth: Int = 0
    var mVideoHeight: Int = 0
    var mVideoVisibleWidth: Int = 0
    var mVideoVisibleHeight: Int = 0
    var mSarNum: Int = 0
    var mSarDen: Int = 0

    private val mHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        setFullScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_player)
        surfaceFrameAddLayoutListener(true)
        mCurrentScreenOrientation = resources.configuration.orientation
        title = ""
        initializeOverlay()
    }

    private fun surfaceFrameAddLayoutListener(add: Boolean) {
        if (mSurfaceFrame == null || add == (mOnLayoutChangeListener != null)) return
        if (add) {
            mOnLayoutChangeListener =
                object : View.OnLayoutChangeListener {
                    private val mRunnable = Runnable { changeSurfaceLayout() }

                    override fun onLayoutChange(
                        v: View,
                        left: Int,
                        top: Int,
                        right: Int,
                        bottom: Int,
                        oldLeft: Int,
                        oldTop: Int,
                        oldRight: Int,
                        oldBottom: Int,
                    ) {
                        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                            /* changeSurfaceLayout need to be called after the layout changed */
                            mHandler.removeCallbacks(mRunnable)
                            mHandler.post(mRunnable)
                        }
                    }
                }
            mSurfaceFrame!!.addOnLayoutChangeListener(mOnLayoutChangeListener)
            changeSurfaceLayout()
        } else {
            mSurfaceFrame!!.removeOnLayoutChangeListener(mOnLayoutChangeListener)
            mOnLayoutChangeListener = null
        }
    }

    override fun onStart() {
        super.onStart()
        initialize()
    }

    override fun onResume() {
        super.onResume()
        mOverlayFragment!!.showOverlays()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setFullScreen()
    }

    override fun onPause() {
        mOverlayFragment!!.hideOverlays()
        super.onPause()
    }

    override fun onStop() {
        cleanup()
        VLCPlayer.release()
        surfaceFrameAddLayoutListener(false)
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
        mCurrentScreenOrientation = newConfig.orientation
        changeSurfaceLayout()
    }

    fun handleIntent(intent: Intent) {
        if (mPlayer == null) return
        setIntent(intent)
        if (Intent.ACTION_VIEW == intent.action) {
            val accel =
                Integer.parseInt(
                    PreferenceManager
                        .getDefaultSharedPreferences(this)
                        .getString(
                            DreamDroid.PREFS_KEY_HWACCEL,
                            Integer.toString(VLCPlayer.MEDIA_HWACCEL_ENABLED),
                        ),
                )
            mPlayer!!.playUri(intent.data!!, accel)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return mOverlayFragment!!.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    private fun initialize() {
        cleanup()
        mPlayer = VLCPlayer.get()

        mSurfaceFrame = findViewById(R.id.player_surface_frame)
        mSurfaceView = findViewById(R.id.player_surface)
        mSubtitlesSurfaceView = findViewById(R.id.subtitles_surface)
        mSubtitlesSurfaceView.setZOrderMediaOverlay(true)
        mSubtitlesSurfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)

        mPlayer!!.attach(this, mSurfaceView, mSubtitlesSurfaceView)

        VLCPlayer.getMediaPlayer()!!.vlcVout.addCallback(this)
        VLCPlayer.getMediaPlayer()!!.setEventListener(this)

        handleIntent(intent)
        setFullScreen()
    }

    private fun initializeOverlay() {
        if (mOverlayFragment != null) return

        mOverlayFragment =
            supportFragmentManager.findFragmentByTag("video_overlay_fragment") as VideoOverlayFragment?
        if (mOverlayFragment != null) return

        mOverlayFragment = VideoOverlayFragment()
        mOverlayFragment!!.arguments = intent.extras
        val ft: FragmentTransaction = supportFragmentManager.beginTransaction()
        ft.replace(R.id.overlay, mOverlayFragment!!, "video_overlay_fragment")
        ft.commit()
    }

    private fun cleanup() {
        cleanup(false)
    }

    private fun cleanup(force: Boolean) {
        if (mPlayer == null && force) mPlayer = VLCPlayer.get()
        if (mPlayer == null) return
        mPlayer!!.detach()
        mPlayer = null
        mSurfaceView = null
        VLCPlayer.getMediaPlayer()!!.vlcVout.removeCallback(this)
        VLCPlayer.getMediaPlayer()!!.setEventListener(null)
    }

    protected fun onMediaPlaying() {
        if (mVideoWidth * mVideoHeight == 0) {
            mVideoHeight = mPlayer!!.getVideoHeight()
            mVideoWidth = mPlayer!!.getVideoWidth()
            mVideoVisibleWidth = mVideoWidth
            mVideoVisibleHeight = mVideoHeight
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(getPipParams())
        }
    }

    protected fun changeSurfaceLayout() {
        if (mPlayer == null) return
        var sw: Int
        var sh: Int

        // get screen size
        sw = window.decorView.width
        sh = window.decorView.height

        // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
        val isPortrait = mCurrentScreenOrientation == Configuration.ORIENTATION_PORTRAIT

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
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

        val surface = mSurfaceView!!
        val subtitlesSurface = mSubtitlesSurfaceView
        val surfaceFrame = mSurfaceFrame!!
        var lp = surface.layoutParams

        if (mVideoWidth * mVideoHeight == 0) {
            mVideoHeight = mPlayer!!.getVideoHeight()
            mVideoWidth = mPlayer!!.getVideoWidth()
            mVideoVisibleWidth = mVideoWidth
            mVideoVisibleHeight = mVideoHeight
        }

        if (mVideoWidth * mVideoHeight == 0 || isInPictureInPictureMode) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width = LayoutParams.MATCH_PARENT
            lp.height = LayoutParams.MATCH_PARENT
            surface.layoutParams = lp
            lp = surfaceFrame.layoutParams
            lp.width = LayoutParams.MATCH_PARENT
            lp.height = LayoutParams.MATCH_PARENT
            surfaceFrame.layoutParams = lp
            if (player != null && mVideoWidth * mVideoHeight == 0) {
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
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth.toDouble()
            ar = mVideoVisibleWidth.toDouble() / mVideoVisibleHeight.toDouble()
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * mSarNum.toDouble() / mSarDen
            ar = vw / mVideoVisibleHeight
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
        lp.width = ceil(dw * mVideoWidth / mVideoVisibleWidth).toInt()
        lp.height = ceil(dh * mVideoHeight / mVideoVisibleHeight).toInt()
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
        mSurfaceView!!.getGlobalVisibleRect(sourceRectHint)
        val ar = Rational(mVideoWidth, mVideoHeight)
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
    override fun onPictureInPictureRequested(): Boolean {
        return doEnterPip()
    }

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
        sarDen: Int,
    ) {
        mVideoWidth = width
        mVideoHeight = height
        mVideoVisibleWidth = visibleWidth
        mVideoVisibleHeight = visibleHeight
        mSarNum = sarNum
        mSarDen = sarDen
        changeSurfaceLayout()
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout) {
        val mediaPlayer = VLCPlayer.getMediaPlayer()!!
        mediaPlayer.setAspectRatio(null)
        mediaPlayer.setScale(0f)
        mediaPlayer.setVideoTrackEnabled(true)
    }

    override fun isInPictureInPictureMode(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && super.isInPictureInPictureMode()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        changeSurfaceLayout()
    }

    override fun onSurfacesDestroyed(vlcVout: IVLCVout) {}

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        if (mOverlayFragment == null) return
        mOverlayFragment!!.onDialogAction(action, details, dialogTag)
    }

    override fun finish() {
        super.finish()
        cleanup()
    }

    override fun onEvent(event: MediaPlayer.Event) {
        mOverlayFragment!!.onUpdateButtons()
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                onMediaPlaying()
                // Intentional fall-through from Playing into ESSelected handling (matches Java switch).
                if (event.esChangedType == IMedia.Track.Type.Video) {
                    changeSurfaceLayout()
                }
            }
            MediaPlayer.Event.ESSelected -> {
                if (event.esChangedType == IMedia.Track.Type.Video) {
                    changeSurfaceLayout()
                }
            }
            MediaPlayer.Event.EndReached,
            MediaPlayer.Event.Stopped,
            -> finish()
        }
        mOverlayFragment!!.onEvent(event)
    }

    companion object {
        @JvmField
        val TAG: String = VideoActivity::class.java.simpleName
    }
}
