package net.reichholf.dreamdroid.video;

import android.content.Context;
import android.net.Uri;
import android.view.SurfaceView;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;

/**
 * Created by reichi on 16/02/16.
 */
public class VLCPlayer {
	static VLCPlayer sPlayer;
	static volatile MediaPlayer sMediaPlayer = null;

	public final static int MEDIA_HWACCEL_DISABLED = 0x00;
	public final static int MEDIA_HWACCEL_ENABLED = 0x01;
	public final static int MEDIA_HWACCEL_FORCE = 0x02;

	protected Media mCurrentMedia;

	public static void release() {
		if(sPlayer == null)
			return;
		sPlayer.deinit();
		sPlayer = null;
	}

	public static VLCPlayer get() {
		if (sPlayer == null)
			sPlayer = new VLCPlayer();
		return sPlayer;
	}

	protected static void init() {
		sMediaPlayer = new MediaPlayer(VLCInstance.get());
		sMediaPlayer.setAspectRatio(null);
		sMediaPlayer.setScale(0);
		sMediaPlayer.setVideoTrackEnabled(true);
		sMediaPlayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
	}

	public void deinit() {
		detach();
		sMediaPlayer.release();
		sMediaPlayer = null;
	}

	public static MediaPlayer getMediaPlayer() {
		if (sMediaPlayer == null)
			init();
		return sMediaPlayer;
	}

	public void attach(IVLCVout.OnNewVideoLayoutListener newVideoLayoutListener,SurfaceView surfaceView, SurfaceView subtitleSurfaceView) {
		final IVLCVout vlcVout = getMediaPlayer().getVLCVout();
		vlcVout.setVideoView(surfaceView);
		vlcVout.setSubtitlesView(subtitleSurfaceView);
		vlcVout.attachViews(newVideoLayoutListener);
	}

	public void detach() {
		stop();
		final IVLCVout vlcVout = getMediaPlayer().getVLCVout();
		vlcVout.detachViews();
	}

	public void setWindowSize(int width, int height) {
		getMediaPlayer().getVLCVout().setWindowSize(width, height);
	}

	public void playUri(Uri uri, int flags) {
		mCurrentMedia = new Media(VLCInstance.get(), uri);
		boolean isHwAccel = (flags & MEDIA_HWACCEL_ENABLED) > 0;
		boolean isHwAccelForce = (flags & MEDIA_HWACCEL_FORCE) > 0;
		mCurrentMedia.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce);
		play();
	}

	public void play() {
		if (mCurrentMedia == null)
			return;
		MediaPlayer mp = getMediaPlayer();
		if (!mCurrentMedia.equals(mp.getMedia()))
			mp.setMedia(mCurrentMedia);
		if (mp.isPlaying() && mp.getRate() == 1.0f)
			mp.pause();
		else
			mp.play();
		mp.setRate(1.0f);
	}

	public long getLength() {
		return getMediaPlayer().getLength();
	}

	public long getTime() {
		return getMediaPlayer().getTime();
	}

	public void setTime(long position) {
		getMediaPlayer().setTime(position);
	}

	public float getPosition() {
		return getMediaPlayer().getPosition();
	}

	public void setPosition(float position) {
		getMediaPlayer().setPosition(position);
	}

	public boolean isSeekable() {
		return getMediaPlayer().isSeekable();
	}

	public boolean faster() {
		if (!isSeekable() || !getMediaPlayer().isPlaying())
			return false;
		float rate = getMediaPlayer().getRate();
		if (rate == -1.0f)
			rate = 0.5f; //multiplied by 2 below
		rate = Math.min(rate * 2, 64);
		getMediaPlayer().setRate(rate);
		return true;
	}

	public boolean slower() {
		if (!isSeekable() || !getMediaPlayer().isPlaying())
			return false;
		float rate = getMediaPlayer().getRate();
		if (rate == 1.0f)
			rate = -1.0f;
		rate = Math.max(rate * 2, -64);
		getMediaPlayer().setRate(rate);
		return true;
	}

	public void stop() {
		getMediaPlayer().stop();
		Media media = getMediaPlayer().getMedia();
		if (media != null) {
			media.setEventListener(null);
			media.release();
		}
	}

	public int getAudioTracksCount() {
		return getMediaPlayer().getAudioTracksCount();
	}

	public int getSubtitleTracksCount() {
		return getMediaPlayer().getSpuTracksCount();
	}

	public int getVideoWidth() {
		Media.VideoTrack track = getMediaPlayer().getCurrentVideoTrack();
		if (track == null)
			return 0;
		return track.width;
	}

	public int getVideoHeight() {
		Media.VideoTrack track = getMediaPlayer().getCurrentVideoTrack();
		if (track == null)
			return 0;
		return track.height;
	}
}
