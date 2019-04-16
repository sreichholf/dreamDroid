package net.reichholf.dreamdroid.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.VideoOverlayFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.video.VLCPlayer;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

/**
 * Created by reichi on 16/02/16.
 */


public class VideoActivity extends AppCompatActivity implements IVLCVout.OnNewVideoLayoutListener, IVLCVout.Callback, ActionDialog.DialogActionListener, MediaPlayer.EventListener {
	public static final String TAG = VideoActivity.class.getSimpleName();

	FrameLayout mSurfaceFrame;
	SurfaceView mSurfaceView;
	SurfaceView mSubtitlesSurfaceView;
	VLCPlayer mPlayer;
	VideoOverlayFragment mOverlayFragment;

	View.OnLayoutChangeListener mOnLayoutChangeListener;

	int mCurrentScreenOrientation;

	int mVideoWidth;
	int mVideoHeight;
	int mVideoVisibleWidth;
	int mVideoVisibleHeight;
	int mSarNum;
	int mSarDen;

	private final Handler mHandler = new Handler(Looper.getMainLooper());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		setFullScreen();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video_player);
		surfaceFrameAddLayoutListener(true);
		mCurrentScreenOrientation = getResources().getConfiguration().orientation;
		setTitle("");
		initializeOverlay();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void surfaceFrameAddLayoutListener(boolean add) {
		if (mSurfaceFrame == null || add == (mOnLayoutChangeListener != null)) return;
		if (add) {
			mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
				private final Runnable mRunnable = () -> changeSurfaceLayout();
				@Override
				public void onLayoutChange(View v, int left, int top, int right,
										   int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
					if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
						/* changeSurfaceLayout need to be called after the layout changed */
						mHandler.removeCallbacks(mRunnable);
						mHandler.post(mRunnable);
					}
				}
			};
			mSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
			changeSurfaceLayout();
		}
		else {
			mSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);
			mOnLayoutChangeListener = null;
		}
	}


	@Override
	protected void onStart() {
		super.onStart();
		initialize();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mOverlayFragment.showOverlays();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if(hasFocus)
			setFullScreen();
	}

	@Override
	protected void onPause() {
		mOverlayFragment.hideOverlays();
		super.onPause();
	}

	@Override
	protected void onStop() {
		cleanup();
		VLCPlayer.release();
		surfaceFrameAddLayoutListener(false);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		handleIntent(intent);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mCurrentScreenOrientation = newConfig.orientation;
		changeSurfaceLayout();
	}

	public void handleIntent(Intent intent) {
		if(mPlayer == null)
			return;
		setIntent(intent);
		if ( Intent.ACTION_VIEW.equals(intent.getAction()) ) {
			int accel = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(DreamDroid.PREFS_KEY_HWACCEL, Integer.toString(VLCPlayer.MEDIA_HWACCEL_ENABLED)));
			mPlayer.playUri(intent.getData(), accel);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return mOverlayFragment.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
	}

	private void initialize() {
		mSurfaceFrame = findViewById(R.id.player_surface_frame);
		mSurfaceView = findViewById(R.id.player_surface);
		mSubtitlesSurfaceView = findViewById(R.id.subtitles_surface);
		mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
		mSubtitlesSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

		if (mPlayer == null)
			mPlayer = VLCPlayer.get();
		mPlayer.attach(this, mSurfaceView, mSubtitlesSurfaceView);

		VLCPlayer.getMediaPlayer().getVLCVout().addCallback(this);
		VLCPlayer.getMediaPlayer().setEventListener(this);

		handleIntent(getIntent());
		setFullScreen();
	}

	private void initializeOverlay() {
		if (mOverlayFragment != null)
			return;

		mOverlayFragment = (VideoOverlayFragment) getSupportFragmentManager().findFragmentByTag("video_overlay_fragment");
		if (mOverlayFragment != null)
			return;

		mOverlayFragment = new VideoOverlayFragment();
		mOverlayFragment.setArguments(getIntent().getExtras());
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.overlay, mOverlayFragment, "video_overlay_fragment");
		ft.commit();
	}

	private void cleanup() {
		if(mPlayer == null)
			return;
		mPlayer.detach();
		mPlayer = null;
		mSurfaceView = null;
		VLCPlayer.getMediaPlayer().getVLCVout().removeCallback(this);
		VLCPlayer.getMediaPlayer().setEventListener(null);
	}

	protected void changeSurfaceLayout() {
		if(mPlayer == null)
			return;
		int sw;
		int sh;

		// get screen size
		sw = getWindow().getDecorView().getWidth();
		sh = getWindow().getDecorView().getHeight();

		// getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
		boolean isPortrait = mCurrentScreenOrientation == Configuration.ORIENTATION_PORTRAIT;

		if (sw > sh && isPortrait || sw < sh && !isPortrait) {
			int w = sw;
			sw = sh;
			sh = w;
		}

		// sanity check
		if (sw * sh == 0) {
			Log.e(TAG, "Invalid surface size");
			return;
		}
		MediaPlayer player = VLCPlayer.getMediaPlayer();
		if (player != null) {
			final IVLCVout vlcVout = player.getVLCVout();
			vlcVout.setWindowSize(sw, sh);
		}

		SurfaceView surface;
		SurfaceView subtitlesSurface;
		FrameLayout surfaceFrame;
		surface = mSurfaceView;
		subtitlesSurface = mSubtitlesSurfaceView;
		surfaceFrame = mSurfaceFrame;
		LayoutParams lp = surface.getLayoutParams();

		if (mVideoWidth * mVideoHeight == 0) {
			mVideoHeight = mPlayer.getVideoHeight();
			mVideoWidth = mPlayer.getVideoWidth();
			mVideoVisibleWidth = mVideoWidth;
			mVideoVisibleHeight = mVideoHeight;
		}


		if (mVideoWidth * mVideoHeight == 0 || isInPictureInPictureMode()) {
			/* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
			lp.width = LayoutParams.MATCH_PARENT;
			lp.height = LayoutParams.MATCH_PARENT;
			surface.setLayoutParams(lp);
			lp = surfaceFrame.getLayoutParams();
			lp.width = LayoutParams.MATCH_PARENT;
			lp.height = LayoutParams.MATCH_PARENT;
			surfaceFrame.setLayoutParams(lp);
			if (player != null && mVideoWidth * mVideoHeight == 0) {
				player.setAspectRatio(null);
				player.setScale(0);
			}
			return;
		}

		if (player != null && lp.width == lp.height && lp.width == LayoutParams.MATCH_PARENT) {
			/* We handle the placement of the video using Android View LayoutParams */
			player.setAspectRatio(null);
			player.setScale(0);
		}

		// compute the aspect ratio
		double ar, vw;
		if (mSarDen == mSarNum) {
			/* No indication about the density, assuming 1:1 */
			vw = mVideoVisibleWidth;
			ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
		} else {
			/* Use the specified aspect ratio */
			vw = mVideoVisibleWidth * (double) mSarNum / mSarDen;
			ar = vw / mVideoVisibleHeight;
		}

		double dw = sw, dh = sh;

		// compute the display aspect ratio
		double dar = dw / dh;
		if (dar < ar)
			dh = dw / ar;
		else
			dw = dh * ar;

		// set display size
		lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
		lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
		surface.setLayoutParams(lp);
		subtitlesSurface.setLayoutParams(lp);

		// set frame size (crop if necessary)
		lp = surfaceFrame.getLayoutParams();
		lp.width = (int) Math.floor(dw);
		lp.height = (int) Math.floor(dh);
		surfaceFrame.setLayoutParams(lp);

		surface.invalidate();
		subtitlesSurface.invalidate();
	}

	public void setFullScreen() {
		int visibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN;
		int navigation = View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
			navigation |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
		visibility |= navigation;
		getWindow().getDecorView().setSystemUiVisibility(visibility);
	}

	@Override
	public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
		mVideoWidth = width;
		mVideoHeight = height;
		mVideoVisibleWidth = visibleWidth;
		mVideoVisibleHeight = visibleHeight;
		mSarNum = sarNum;
		mSarDen = sarDen;
		changeSurfaceLayout();
	}

	@Override
	public void onSurfacesCreated(IVLCVout vlcVout) {
		MediaPlayer mediaPlayer = VLCPlayer.getMediaPlayer();
		mediaPlayer.setAspectRatio(null);
		mediaPlayer.setScale(0);
		mediaPlayer.setVideoTrackEnabled(true);
	}

	@Override
	public boolean isInPictureInPictureMode() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && super.isInPictureInPictureMode();
	}

	@Override
	public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode);
		changeSurfaceLayout();
	}

	@Override
	public void onSurfacesDestroyed(IVLCVout vlcVout) {
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if(mOverlayFragment == null)
			return;
		mOverlayFragment.onDialogAction(action, details, dialogTag);
	}

	@Override
	public void finish() {
		super.finish();
		cleanup();
	}

	@Override
	public void onEvent(MediaPlayer.Event event) {
		mOverlayFragment.onUpdateButtons();
		switch(event.type){
			case MediaPlayer.Event.ESSelected:
				if (event.getEsChangedType() == Media.VideoTrack.Type.Video)
					changeSurfaceLayout();
				break;
			case MediaPlayer.Event.EndReached:
			case MediaPlayer.Event.Stopped:
				finish();
		}
		mOverlayFragment.onEvent(event);
	}
}
