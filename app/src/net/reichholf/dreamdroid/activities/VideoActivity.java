package net.reichholf.dreamdroid.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.vlc.VLCPlayer;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.MediaPlayer;

import java.util.Calendar;
import java.util.HashMap;

/**
 * Created by reichi on 16/02/16.
 */


public class VideoActivity extends AppCompatActivity implements IVLCVout.Callback {
	static float sOverlayAlpha = 0.7f;

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
		setFullScreen();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video_player);
	}


	@Override
	protected void onResume() {
		super.onResume();
		mOverlayFragment = new VideoOverlayFragment();
		mOverlayFragment.setArguments(getIntent().getExtras());

		findViewById(R.id.overlay).setOnTouchListener(new View.OnTouchListener() {
			private static final int MAX_CLICK_DURATION = 200;
			private long startClickTime;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN: {
						startClickTime = Calendar.getInstance().getTimeInMillis();
						break;
					}
					case MotionEvent.ACTION_UP: {
						long clickDuration = Calendar.getInstance().getTimeInMillis() - startClickTime;
						if (clickDuration < MAX_CLICK_DURATION) {
							mOverlayFragment.toggleViews();
						}
					}
				}
				return true;
			}
		});

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.overlay, mOverlayFragment);
		ft.commit();

		mVideoSurface = (SurfaceView) findViewById(R.id.video_surface);
		mPlayer = new VLCPlayer();
		mPlayer.attach(mVideoSurface);
		VLCPlayer.getMediaPlayer().getVLCVout().addCallback(this);
		VLCPlayer.getMediaPlayer().setEventListener(mOverlayFragment);
		if (getIntent().getAction() == Intent.ACTION_VIEW) {
			mPlayer.playUri(getIntent().getData());
		}
	}

	@Override
	protected void onPause() {
		mPlayer.detach();
		mPlayer = null;
		mVideoSurface = null;
		VLCPlayer.getMediaPlayer().getVLCVout().removeCallback(this);
		VLCPlayer.getMediaPlayer().setEventListener(null);

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.remove(mOverlayFragment);
		ft.commit();
		super.onPause();
	}

	protected void setupVideoSurface() {
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
		mVideoSurface.invalidate();
	}

	public void setFullScreen() {
		int visibility = 0;
		visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			visibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
			visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
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
	}

	@Override
	public void onSurfacesDestroyed(IVLCVout vlcVout) {
	}

	public class VideoOverlayFragment extends Fragment implements MediaPlayer.EventListener {
		public final String TITLE = "title";
		protected String mServiceName;
		protected ExtendedHashMap mServiceInfo;
		protected Handler mHandler;
		protected Runnable mAutoHideRunnable;

		@Override
		public void onCreate(@Nullable Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			mServiceName = getArguments().getString(TITLE);
			HashMap<String, Object> serviceInfo = (HashMap<String, Object>) getArguments().get("serviceInfo");
			if (serviceInfo != null)
				mServiceInfo = new ExtendedHashMap(serviceInfo);
			else
				mServiceInfo = null;
			mHandler = new Handler();
			mAutoHideRunnable = new Runnable() {
				@Override
				public void run() {
					hideOverlays();
				}
			};
			autohide();
		}

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.video_player_overlay, container, false);
			TextView serviceName = (TextView) view.findViewById(R.id.service_name);
			serviceName.setText(mServiceName);

			if (mServiceInfo != null) {
				TextView eventTitle = (TextView) view.findViewById(R.id.event_title);
				//TextView eventDescription = (TextView) view.findViewById(R.id.event_description);

				eventTitle.setText(mServiceInfo.getString(Event.KEY_EVENT_TITLE));
				//eventDescription.setText(mServiceInfo.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED));
			}

			ImageButton stop = (ImageButton) view.findViewById(R.id.close);
			stop.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					getActivity().finish();
				}
			});

			return view;
		}

		@Override
		public void onResume() {
			super.onResume();
			final View snroot = getView().findViewById(R.id.service_name_root);
			final View sdroot = getView().findViewById(R.id.service_detail_root);
			snroot.setVisibility(View.GONE);
			sdroot.setVisibility(View.GONE);
			showOverlays();
		}

		@Override
		public void onPause() {
			mHandler.removeCallbacks(mAutoHideRunnable);
			super.onPause();
		}

		public void autohide() {
			mHandler.postDelayed(mAutoHideRunnable, 10000);
		}

		public void showOverlays() {
			final View snroot = getView().findViewById(R.id.service_name_root);
			final View sdroot = getView().findViewById(R.id.service_detail_root);
			fadeInView(snroot);
			if (mServiceInfo != null)
				fadeInView(sdroot);
			else
				sdroot.setVisibility(View.GONE);
			autohide();
		}

		public void hideOverlays() {
			mHandler.removeCallbacks(mAutoHideRunnable);
			final View snroot = getView().findViewById(R.id.service_name_root);
			final View sdroot = getView().findViewById(R.id.service_detail_root);
			fadeOutView(snroot);
			fadeOutView(sdroot);
		}

		private void fadeInView(final View v) {
			if (v.getVisibility() == View.VISIBLE)
				return;
			v.setVisibility(View.VISIBLE);
			v.setAlpha(0.0f);
			v.animate().alpha(sOverlayAlpha).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					v.setAlpha(sOverlayAlpha);
				}
			});
		}

		private void fadeOutView(final View v) {
			if (v.getVisibility() == View.GONE)
				return;
			v.animate().alpha(0.0f).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					v.setVisibility(View.GONE);
				}
			});
		}

		public void toggleViews() {
			View snroot = getView().findViewById(R.id.service_name_root);
			if (snroot.getVisibility() == View.VISIBLE)
				hideOverlays();
			else
				showOverlays();
		}

		@Override
		public void onEvent(MediaPlayer.Event event) {
			switch (event.type) {
				case MediaPlayer.Event.Opening: {
					View progressView = getView().findViewById(R.id.progress);
					fadeInView(progressView);
					break;
				}
				case MediaPlayer.Event.Playing: {
					View progressView = getView().findViewById(R.id.progress);
					fadeOutView(progressView);
					break;
				}
				case MediaPlayer.Event.EncounteredError:
					Toast.makeText(getActivity(), R.string.playback_failed, Toast.LENGTH_LONG).show();
					break;
				case MediaPlayer.Event.EndReached:
				case MediaPlayer.Event.Stopped:
					getActivity().finish();
				default:
					break;
			}
		}
	}
}
