package net.reichholf.dreamdroid.vlc;

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
	static LibVLC sLibVLC = new LibVLC();
	static MediaPlayer sMediaPlayer;


    public final static int MEDIA_HWACCEL_DISABLED = 0x00;
    public final static int MEDIA_HWACCEL_ENABLED = 0x01;
    public final static int MEDIA_HWACCEL_FORCE = 0x02;

	protected Media mCurrentMedia;

	static {
		ArrayList<String> options = new ArrayList<>();
		options.add("--http-reconnect");
		sLibVLC = new LibVLC(options);
		sMediaPlayer = new MediaPlayer(sLibVLC);
	}

	public static LibVLC getLibVLC() {
		return sLibVLC;
	}

	public static MediaPlayer getMediaPlayer() {
		return sMediaPlayer;
	}

	public void attach(SurfaceView surfaceView, SurfaceView subtitleSurfaceView) {
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
		mCurrentMedia = new Media(getLibVLC(), uri);
        boolean isHwAccel = (flags & MEDIA_HWACCEL_ENABLED) > 0;
        boolean isHwAccelForce = (flags & MEDIA_HWACCEL_FORCE) > 0;
        mCurrentMedia.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce );
		play();
	}

	public void play() {
		if (mCurrentMedia == null)
			return;
		getMediaPlayer().setMedia(mCurrentMedia);
		getMediaPlayer().play();
	}

	public void stop() {
		getMediaPlayer().stop();
	}
}
