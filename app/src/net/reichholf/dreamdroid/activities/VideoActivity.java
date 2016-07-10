package net.reichholf.dreamdroid.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter;
import net.reichholf.dreamdroid.fragment.VideoOverlayFragment;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EpgNowNextListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.vlc.VLCPlayer;
import net.reichholf.dreamdroid.widget.AutofitRecyclerView;
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport;
import net.reichholf.dreamdroid.widget.helper.SpacesItemDecoration;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by reichi on 16/02/16.
 */


public class VideoActivity extends AppCompatActivity implements IVLCVout.Callback {
	public static final String TAG = VideoActivity.class.getSimpleName();

	SurfaceView mVideoSurface;
	VLCPlayer mPlayer;
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
		initializeOverlay();
	}


	public void handleIntent(Intent intent) {
		setIntent(intent);
		if (intent.getAction() == Intent.ACTION_VIEW) {
			int accel = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(DreamDroid.PREFS_KEY_HWACCEL, Integer.toString(VLCPlayer.MEDIA_HWACCEL_ENABLED)));
			mPlayer.playUri(intent.getData(), accel);
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
		mOverlayFragment.showOverlays(true);
	}

	@Override
	protected void onPause() {
		mOverlayFragment.hideOverlays();
		super.onPause();
	}

	@Override
	protected void onStop() {
		cleanup();
		super.onStop();
	}

	@Override
	public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
		super.onMultiWindowModeChanged(isInMultiWindowMode);
		setupVideoSurface();
	}

	@Override
	public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode);
		setupVideoSurface();
	}

	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setupVideoSurface();
	}

	private void initialize() {
		mVideoSurface = (SurfaceView) findViewById(R.id.video_surface);
		if (mPlayer == null)
			mPlayer = new VLCPlayer();
		mPlayer.attach(mVideoSurface);
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

		//We have to reverse width/height after orientation change (we don't let android handle it to keep the video running)
		boolean isPortraitMode = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
		if (isPortraitMode && surfaceWidth > surfaceHeight || !isPortraitMode && surfaceWidth < surfaceHeight) {
			displayWidth = surfaceHeight;
			displayHeight = surfaceWidth;
		} else {
			displayWidth = surfaceWidth;
			displayHeight = surfaceHeight;
		}

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
		mVideoSurface.invalidate();
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
}
