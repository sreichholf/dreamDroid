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
	static LibVLC sLibVLC = null;
	static MediaPlayer sMediaPlayer = null;

	public final static int MEDIA_HWACCEL_DISABLED = 0x00;
	public final static int MEDIA_HWACCEL_ENABLED = 0x01;
	public final static int MEDIA_HWACCEL_FORCE = 0x02;

	protected Media mCurrentMedia;

	public static void init(Context context) {
		ArrayList<String> options = new ArrayList<>();
		options.add("--http-reconnect");
		sLibVLC = new LibVLC(context, options);
		sMediaPlayer = new MediaPlayer(sLibVLC);
	}

	public void deinit() {
		detach();
		sLibVLC = null;
		sMediaPlayer = null;
	}

	public static MediaPlayer getMediaPlayer() {
		return sMediaPlayer;
	}

	public void attach(SurfaceView surfaceView, SurfaceView subtitleSurfaceView) {
		if (sLibVLC == null || sMediaPlayer == null)
			init(surfaceView.getContext());
		final IVLCVout vlcVout = getMediaPlayer().getVLCVout();
		vlcVout.setVideoView(surfaceView);
		vlcVout.setSubtitlesView(subtitleSurfaceView);
		vlcVout.attachViews();
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
		mCurrentMedia = new Media(sLibVLC, uri);
		boolean isHwAccel = (flags & MEDIA_HWACCEL_ENABLED) > 0;
		boolean isHwAccelForce = (flags & MEDIA_HWACCEL_FORCE) > 0;
		mCurrentMedia.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce);
		play();
	}

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

	public void stop() {
		getMediaPlayer().stop();
	}

	public int getAudioTracksCount() {
		return getMediaPlayer().getAudioTracksCount();
	}

	public int getSubtitleTracksCount() {
		return getMediaPlayer().getSpuTracksCount();
	}
}
