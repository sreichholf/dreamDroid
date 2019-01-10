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
public class VLCPlayer implements VideoPlayer {
	static volatile MediaPlayer sMediaPlayer = null;

	public final static int MEDIA_HWACCEL_DISABLED = 0x00;
	public final static int MEDIA_HWACCEL_ENABLED = 0x01;
	public final static int MEDIA_HWACCEL_FORCE = 0x02;

	protected Media mCurrentMedia;

	public static void init() {
		sMediaPlayer = new MediaPlayer(VLCInstance.get());
		sMediaPlayer.setAspectRatio(null);
		sMediaPlayer.setScale(0);
		sMediaPlayer.setVideoTrackEnabled(true);
		sMediaPlayer.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0);
	}

	@Override
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

	@Override
	public void attach(IVLCVout.OnNewVideoLayoutListener newVideoLayoutListener,SurfaceView surfaceView, SurfaceView subtitleSurfaceView) {
		final IVLCVout vlcVout = getMediaPlayer().getVLCVout();
		vlcVout.setVideoView(surfaceView);
		vlcVout.setSubtitlesView(subtitleSurfaceView);
		vlcVout.attachViews(newVideoLayoutListener);
	}

	@Override
	public void detach() {
		stop();
		final IVLCVout vlcVout = getMediaPlayer().getVLCVout();
		vlcVout.detachViews();
	}

	@Override
	public void setWindowSize(int width, int height) {
		getMediaPlayer().getVLCVout().setWindowSize(width, height);
	}

	@Override
	public void playUri(Uri uri, int flags) {
		mCurrentMedia = new Media(VLCInstance.get(), uri);
		boolean isHwAccel = (flags & MEDIA_HWACCEL_ENABLED) > 0;
		boolean isHwAccelForce = (flags & MEDIA_HWACCEL_FORCE) > 0;
		mCurrentMedia.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce);
		play();
	}

	@Override
	public void play() {
		if (mCurrentMedia == null)
			return;
		getMediaPlayer().setMedia(mCurrentMedia);
		getMediaPlayer().play();
	}

	@Override
	public long getLength() {
		return getMediaPlayer().getLength();
	}

	@Override
	public long getTime() {
		return getMediaPlayer().getTime();
	}

	@Override
	public void setTime(long position) {
		getMediaPlayer().setTime(position);
	}

	@Override
	public float getPosition() {
		return getMediaPlayer().getPosition();
	}

	@Override
	public void setPosition(float position) {
		getMediaPlayer().setPosition(position);
	}

	@Override
	public boolean isSeekable() {
		return getMediaPlayer().isSeekable();
	}

	@Override
	public void stop() {
		getMediaPlayer().stop();
		Media media = getMediaPlayer().getMedia();
		if (media != null) {
			media.setEventListener(null);
			media.release();
		}
	}

	@Override
	public int getAudioTracksCount() {
		return getMediaPlayer().getAudioTracksCount();
	}

	@Override
	public int getSubtitleTracksCount() {
		return getMediaPlayer().getSpuTracksCount();
	}

	@Override
	public int getVideoWidth() {
		Media.VideoTrack track = getMediaPlayer().getCurrentVideoTrack();
		if (track == null)
			return 0;
		return track.width;
	}

	@Override
	public int getVideoHeight() {
		Media.VideoTrack track = getMediaPlayer().getCurrentVideoTrack();
		if (track == null)
			return 0;
		return track.height;
	}
}
