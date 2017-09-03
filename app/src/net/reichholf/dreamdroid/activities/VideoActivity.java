package net.reichholf.dreamdroid.activities;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.VideoOverlayFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.video.VLCPlayer;
import net.reichholf.dreamdroid.video.VideoPlayer;
import net.reichholf.dreamdroid.video.VideoPlayerFactory;

import org.piwik.sdk.PiwikApplication;
import org.piwik.sdk.TrackHelper;
import org.videolan.libvlc.IVLCVout;

/**
 * Created by reichi on 16/02/16.
 */


public class VideoActivity extends AppCompatActivity implements IVLCVout.Callback, ActionDialog.DialogActionListener {
	public static final String TAG = VideoActivity.class.getSimpleName();

	SurfaceView mVideoSurface;
	SurfaceView mSubtitlesSurface;
	VideoPlayer mPlayer;
	VideoOverlayFragment mOverlayFragment;

	int mVideoWidth;
	int mVideoHeight;
	int mVideoVisibleWidth;
	int mVideoVisibleHeight;
	int mSarNum;
	int mSarDen;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		setFullScreen();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video_player);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		setTitle("");
		initializeOverlay();
		if (DreamDroid.isTrackingEnabled(this))
			TrackHelper.track().screen("/" + getClass().getSimpleName()).title(getClass().getSimpleName()).with((PiwikApplication) getApplication());
		getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				setupVideoSurface();
			}
		});
	}

	@Override
	protected void onStart() {
		VideoPlayerFactory.init(this);
		super.onStart();
		initialize();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mOverlayFragment.showOverlays(true);
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
		VideoPlayerFactory.deinit();
		super.onStop();
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

	public void handleIntent(Intent intent) {
		if(mPlayer == null)
			return;
		setIntent(intent);
		if (intent.getAction() == Intent.ACTION_VIEW) {
			int accel = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(DreamDroid.PREFS_KEY_HWACCEL, Integer.toString(VLCPlayer.MEDIA_HWACCEL_ENABLED)));
			mPlayer.playUri(intent.getData(), accel);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(!mOverlayFragment.onKeyDown(keyCode, event))
			return super.onKeyDown(keyCode, event);
		return true;
	}

	private void initialize() {
		mVideoSurface = (SurfaceView) findViewById(R.id.video_surface);
		mSubtitlesSurface = (SurfaceView) findViewById(R.id.subtitles_surface);
		mSubtitlesSurface.setZOrderMediaOverlay(true);
		mSubtitlesSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);

		if (mPlayer == null)
			mPlayer = VideoPlayerFactory.getInstance();
		mPlayer.attach(mVideoSurface, mSubtitlesSurface);

		VLCPlayer.getMediaPlayer().getVLCVout().addCallback(this);
		VLCPlayer.getMediaPlayer().setEventListener(mOverlayFragment);

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
		mVideoSurface = null;
		VLCPlayer.getMediaPlayer().getVLCVout().removeCallback(this);
		VLCPlayer.getMediaPlayer().setEventListener(null);
	}

	protected void setupVideoSurface() {
		if(mPlayer == null)
			return;
		int surfaceWidth;
		int surfaceHeight;

		surfaceWidth = getWindow().getDecorView().getWidth();
		surfaceHeight = getWindow().getDecorView().getHeight();
		mPlayer.setWindowSize(surfaceWidth, surfaceHeight);

		if (mSarDen == mSarNum) {
			mSarDen = 1;
			mSarNum = 1;
		}

		double videoAspect, videoWith, displayAspect, displayWidth, displayHeight;
		displayWidth = surfaceWidth;
		displayHeight = surfaceHeight;

		videoWith = mVideoVisibleWidth * (double) mSarNum / mSarDen;
		videoAspect = videoWith / (double) mVideoVisibleHeight;
		displayAspect = displayWidth / displayHeight;

		if (displayAspect < videoAspect)
			displayHeight = displayWidth / videoAspect;
		else
			displayWidth = displayHeight * videoAspect;
		ViewGroup.LayoutParams layoutParams = mVideoSurface.getLayoutParams();
		layoutParams.width = (int) Math.floor(displayWidth * mVideoWidth / mVideoVisibleWidth);
		layoutParams.height = (int) Math.floor(displayHeight * mVideoHeight / mVideoVisibleHeight);
		mVideoSurface.setLayoutParams(layoutParams);
		mSubtitlesSurface.setLayoutParams(layoutParams);
		mVideoSurface.invalidate();
		mSubtitlesSurface.invalidate();
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
	public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
		mVideoWidth = width;
		mVideoHeight = height;
		mVideoVisibleWidth = visibleWidth;
		mVideoVisibleHeight = visibleHeight;
		mSarNum = sarNum;
		mSarDen = sarDen;

		setupVideoSurface();
	}

	@Override
	public void onSurfacesCreated(IVLCVout vlcVout) {
		//TODO onSurfacesCreated
	}

	@Override
	public void onSurfacesDestroyed(IVLCVout vlcVout) {
		//TODO onSurfacesDestroyed
	}


	@Override
	public void onHardwareAccelerationError(IVLCVout vlcVout) {
		//TODO onHardwareAccelerationError
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
}
