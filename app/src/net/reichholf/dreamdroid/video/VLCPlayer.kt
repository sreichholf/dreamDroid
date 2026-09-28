package net.reichholf.dreamdroid.video

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import kotlin.math.max
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout

/**
 * Thin Kotlin port of the libVLC [MediaPlayer] singleton wrapper (Phase 2.5e).
 */
class VLCPlayer(private val context: Context) {
    private var currentMedia: Media? = null

    fun deinit() {
        detach()
        vlcMediaPlayer?.release()
        vlcMediaPlayer = null
    }

    fun attach(
        newVideoLayoutListener: IVLCVout.OnNewVideoLayoutListener?,
        surfaceView: SurfaceView?,
        subtitleSurfaceView: SurfaceView?
    ) {
        val vlcVout = getMediaPlayer(context)?.vlcVout ?: return
        if (vlcVout.areViewsAttached()) {
            vlcVout.detachViews()
        }
        vlcVout.setVideoView(surfaceView)
        vlcVout.setSubtitlesView(subtitleSurfaceView)
        vlcVout.attachViews(newVideoLayoutListener)
    }

    fun detach() {
        val mp = vlcMediaPlayer ?: return
        val vlcVout = mp.vlcVout
        if (!VideoPlayback.shouldDetachViews(true, vlcVout.areViewsAttached())) {
            return
        }
        stop()
        vlcVout.detachViews()
    }

    fun setWindowSize(width: Int, height: Int) {
        getMediaPlayer(context)!!.vlcVout.setWindowSize(width, height)
    }

    fun playUri(uri: Uri, flags: Int) {
        val previous = currentMedia
        val media = Media(VLCInstance.get(context), uri)
        val isHwAccel = flags and MEDIA_HWACCEL_ENABLED > 0
        val isHwAccelForce = flags and MEDIA_HWACCEL_FORCE > 0
        media.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce)
        currentMedia = media
        val mp = getMediaPlayer(context) ?: return
        if (previous != null && previous !== media) {
            previous.setEventListener(null)
            previous.release()
        }
        mp.media = media
        mp.rate = 1.0f
        mp.play()
    }

    fun play() {
        val media = currentMedia ?: return
        val mp = getMediaPlayer(context)!!
        val sameMedia = media == mp.media
        if (!sameMedia) {
            mp.media = media
        }
        if (VideoPlayback.shouldTogglePause(mp.isPlaying, mp.rate, sameMedia)) {
            mp.pause()
        } else {
            mp.play()
        }
        mp.rate = 1.0f
    }

    fun getLength(): Long = getMediaPlayer(context)!!.length

    fun getTime(): Long = getMediaPlayer(context)!!.time

    fun getPosition(): Float = getMediaPlayer(context)!!.position

    fun setPosition(position: Float) {
        getMediaPlayer(context)!!.position = position
    }

    fun isSeekable(): Boolean = getMediaPlayer(context)!!.isSeekable

    fun slower(): Boolean {
        if (!isSeekable() || !getMediaPlayer(context)!!.isPlaying) return false
        var rate = getMediaPlayer(context)!!.rate
        if (rate == 1.0f) {
            rate = -1.0f
        }
        rate = max(rate * 2, -64f)
        getMediaPlayer(context)!!.rate = rate
        return true
    }

    fun stop() {
        val mp = vlcMediaPlayer ?: return
        mp.stop()
        val media = mp.media as Media?
        if (media != null) {
            media.setEventListener(null)
            media.release()
        }
    }

    fun getAudioTracksCount(): Int = getMediaPlayer(context)!!.audioTracksCount

    fun getSubtitleTracksCount(): Int = getMediaPlayer(context)!!.spuTracksCount

    fun getVideoWidth(): Int {
        val track = getMediaPlayer(context)!!.currentVideoTrack ?: return 0
        return track.width
    }

    fun getVideoHeight(): Int {
        val track = getMediaPlayer(context)!!.currentVideoTrack ?: return 0
        return track.height
    }

    companion object {
        var player: VLCPlayer? = null

        @Volatile
        var vlcMediaPlayer: MediaPlayer? = null

        const val MEDIA_HWACCEL_DISABLED = 0x00
        const val MEDIA_HWACCEL_ENABLED = 0x01
        const val MEDIA_HWACCEL_FORCE = 0x02

        fun release() {
            val current = player ?: return
            current.deinit()
            player = null
        }

        fun get(context: Context): VLCPlayer? {
            if (player == null) {
                player = VLCPlayer(context.applicationContext)
            }
            return player
        }

        private fun init(context: Context) {
            val mp = MediaPlayer(VLCInstance.get(context))
            mp.setAspectRatio(null)
            mp.setScale(0f)
            mp.setVideoTrackEnabled(true)
            mp.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0)
            vlcMediaPlayer = mp
        }

        fun getMediaPlayer(context: Context): MediaPlayer? {
            if (vlcMediaPlayer == null) {
                init(context)
            }
            return vlcMediaPlayer
        }
    }
}
