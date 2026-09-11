package net.reichholf.dreamdroid.video

import android.net.Uri
import android.view.SurfaceView
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import kotlin.math.max
import kotlin.math.min

/**
 * Thin Kotlin port of the libVLC [MediaPlayer] singleton wrapper (Phase 2.5e).
 * Java callers keep the same static/instance API as the former `VLCPlayer.java`.
 */
class VLCPlayer {
    @JvmField
    protected var mCurrentMedia: Media? = null

    fun deinit() {
        detach()
        sMediaPlayer!!.release()
        sMediaPlayer = null
    }

    fun attach(
        newVideoLayoutListener: IVLCVout.OnNewVideoLayoutListener?,
        surfaceView: SurfaceView?,
        subtitleSurfaceView: SurfaceView?,
    ) {
        val vlcVout = getMediaPlayer()!!.vlcVout
        vlcVout.setVideoView(surfaceView)
        vlcVout.setSubtitlesView(subtitleSurfaceView)
        vlcVout.attachViews(newVideoLayoutListener)
    }

    fun detach() {
        stop()
        getMediaPlayer()!!.vlcVout.detachViews()
    }

    fun setWindowSize(width: Int, height: Int) {
        getMediaPlayer()!!.vlcVout.setWindowSize(width, height)
    }

    fun playUri(uri: Uri, flags: Int) {
        mCurrentMedia = Media(VLCInstance.get(), uri)
        val isHwAccel = flags and MEDIA_HWACCEL_ENABLED > 0
        val isHwAccelForce = flags and MEDIA_HWACCEL_FORCE > 0
        mCurrentMedia!!.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce)
        play()
    }

    fun play() {
        val media = mCurrentMedia ?: return
        val mp = getMediaPlayer()!!
        if (media != mp.media) {
            mp.media = media
        }
        if (mp.isPlaying && mp.rate == 1.0f) {
            mp.pause()
        } else {
            mp.play()
        }
        mp.rate = 1.0f
    }

    fun getLength(): Long = getMediaPlayer()!!.length

    fun getTime(): Long = getMediaPlayer()!!.time

    fun setTime(position: Long) {
        getMediaPlayer()!!.time = position
    }

    fun getPosition(): Float = getMediaPlayer()!!.position

    fun setPosition(position: Float) {
        getMediaPlayer()!!.position = position
    }

    fun isSeekable(): Boolean = getMediaPlayer()!!.isSeekable

    fun faster(): Boolean {
        if (!isSeekable() || !getMediaPlayer()!!.isPlaying) return false
        var rate = getMediaPlayer()!!.rate
        if (rate == -1.0f) {
            rate = 0.5f // multiplied by 2 below
        }
        rate = min(rate * 2, 64f)
        getMediaPlayer()!!.rate = rate
        return true
    }

    fun slower(): Boolean {
        if (!isSeekable() || !getMediaPlayer()!!.isPlaying) return false
        var rate = getMediaPlayer()!!.rate
        if (rate == 1.0f) {
            rate = -1.0f
        }
        rate = max(rate * 2, -64f)
        getMediaPlayer()!!.rate = rate
        return true
    }

    fun stop() {
        getMediaPlayer()!!.stop()
        val media = getMediaPlayer()!!.media as Media?
        if (media != null) {
            media.setEventListener(null)
            media.release()
        }
    }

    fun getAudioTracksCount(): Int = getMediaPlayer()!!.audioTracksCount

    fun getSubtitleTracksCount(): Int = getMediaPlayer()!!.spuTracksCount

    fun getVideoWidth(): Int {
        val track = getMediaPlayer()!!.currentVideoTrack ?: return 0
        return track.width
    }

    fun getVideoHeight(): Int {
        val track = getMediaPlayer()!!.currentVideoTrack ?: return 0
        return track.height
    }

    companion object {
        @JvmField
        var sPlayer: VLCPlayer? = null

        @JvmField
        @Volatile
        var sMediaPlayer: MediaPlayer? = null

        const val MEDIA_HWACCEL_DISABLED = 0x00
        const val MEDIA_HWACCEL_ENABLED = 0x01
        const val MEDIA_HWACCEL_FORCE = 0x02

        @JvmStatic
        fun release() {
            val player = sPlayer ?: return
            player.deinit()
            sPlayer = null
        }

        @JvmStatic
        fun get(): VLCPlayer? {
            if (sPlayer == null) {
                sPlayer = VLCPlayer()
            }
            return sPlayer
        }

        @JvmStatic
        protected fun init() {
            val mp = MediaPlayer(VLCInstance.get())
            mp.setAspectRatio(null)
            mp.setScale(0f)
            mp.setVideoTrackEnabled(true)
            mp.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0)
            sMediaPlayer = mp
        }

        @JvmStatic
        fun getMediaPlayer(): MediaPlayer? {
            if (sMediaPlayer == null) {
                init()
            }
            return sMediaPlayer
        }
    }
}
