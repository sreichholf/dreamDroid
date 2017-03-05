package net.reichholf.dreamdroid.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.VideoActivity;
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
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
import net.reichholf.dreamdroid.video.VLCPlayer;
import net.reichholf.dreamdroid.video.VideoPlayer;
import net.reichholf.dreamdroid.video.VideoPlayerFactory;
import net.reichholf.dreamdroid.widget.AutofitRecyclerView;
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport;
import net.reichholf.dreamdroid.widget.helper.SpacesItemDecoration;

import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class VideoOverlayFragment extends Fragment implements MediaPlayer.EventListener,
		LoaderManager.LoaderCallbacks<LoaderResult<ArrayList<ExtendedHashMap>>>, ItemClickSupport.OnItemClickListener, ActionDialog.DialogActionListener {

	public static final String DIALOG_TAG_AUDIO_TRACK = "dialog_audio_track";
	public static final String DIALOG_TAG_SUBTITLE_TRACK = "dialog_subtitle_track";

	private static final int AUTOHIDE_DEFAULT_TIMEOUT = 7000;

	private static final String LOG_TAG = VideoOverlayFragment.class.getSimpleName();
	private final int[] sOverlayViews = {R.id.service_detail_root};
	private final int[] sZapOverlayViews = {R.id.servicelist};
	static float sOverlayAlpha = 0.85f;

	public final String TITLE = "title";
	public final String SERVICE_INFO = "serviceInfo";
	public final String BOUQUET_REFERENCE = "bouquetRef";
	public final String SERVICE_REFERENCE = "serviceRef";

	protected int mSurfaceHeight;
	protected int mSurfaceWidth;

	protected String mServiceName;
	protected String mServiceRef;
	protected String mBouquetRef;

	protected ArrayList<ExtendedHashMap> mServiceList;
	protected ExtendedHashMap mServiceInfo;

	protected Handler mHandler;
	protected Runnable mAutoHideRunnable;
	protected Runnable mIssueReloadRunnable;

	protected AutofitRecyclerView mServicesView;
	protected ItemClickSupport mItemClickSupport;
	private GestureDetectorCompat mGestureDector;
	private AudioManager mAudioManager;
	private int mAudioMaxVol;
	private float mVolume;
	private boolean mIsHiding;

	public VideoOverlayFragment() {
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		setRetainInstance(true);
		setHasOptionsMenu(true);
		super.onCreate(savedInstanceState);
		mServiceName = getArguments().getString(TITLE);
		mServiceRef = getArguments().getString(SERVICE_REFERENCE);
		mBouquetRef = getArguments().getString(BOUQUET_REFERENCE);
		mServiceList = new ArrayList<>();
		HashMap<String, Object> serviceInfo = (HashMap<String, Object>) getArguments().get(SERVICE_INFO);
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
		mIssueReloadRunnable = new Runnable() {
			@Override
			public void run() {
				reload();
			}
		};

		mAudioManager = (AudioManager) getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
		mAudioMaxVol = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		mVolume = -1f;

		autohide();
		reload();
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.video_player_overlay, container, false);
		mServicesView = (AutofitRecyclerView) view.findViewById(R.id.servicelist);
		mServicesView.setLayoutManager(new GridLayoutManager(getActivity(), 1));
		mServicesView.addItemDecoration(new SpacesItemDecoration(getActivity().getResources().getDimensionPixelSize(R.dimen.recylcerview_content_margin)));

		mServicesView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			int sTreshold = 20;
			int mTotalDistance = 0;

			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				mTotalDistance += dy;
				if (mTotalDistance < 0 - sTreshold) {
					mTotalDistance = 0;
					showToolbar();
				} else if (mTotalDistance > sTreshold) {
					mTotalDistance = 0;
					hideToolbar();
				}
			}
		});
		mItemClickSupport = ItemClickSupport.addTo(mServicesView);
		mItemClickSupport.setOnItemClickListener(this);

		ServiceAdapter adapter = new ServiceAdapter(getActivity(), mServiceList);
		mServicesView.setAdapter(adapter);
		mServicesView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
				super.onScrollStateChanged(recyclerView, newState);
				if (newState == RecyclerView.SCROLL_STATE_IDLE)
					autohide();
				else
					mHandler.removeCallbacks(mAutoHideRunnable);
			}
		});

		mGestureDector = new GestureDetectorCompat(view.findViewById(R.id.overlay_root).getContext(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				boolean isGesturesEnabled = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES, true);
				if (!isGesturesEnabled)
					return true;

				Log.d(LOG_TAG, String.format("distanceY=%s, DeltaY=%s", distanceY, e1.getY() - e2.getY()));
				DisplayMetrics metrics = new DisplayMetrics();
				getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
				boolean isRight = e1.getRawX() > (4 * metrics.widthPixels / 7);
				boolean isLeft = e1.getRawX() < (3 * metrics.widthPixels / 7);

				if (Math.abs(distanceY) > Math.abs(distanceX) && distanceY != 0f) {
					if (isRight)
						onVolumeTouch(distanceY);
					else if (isLeft)
						onBrightnessTouch(distanceY);
				} else if (Math.abs(distanceX) > Math.abs(distanceY) && distanceX != 0f) {
					//TODO: prev/next gesture handling)
				}
				return true;
			}

			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				toggleViews();
				return true;
			}
		});

		view.findViewById(R.id.overlay_root).setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				DisplayMetrics metrics = new DisplayMetrics();
				getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
				if (mSurfaceHeight == 0)
					mSurfaceHeight = Math.min(metrics.widthPixels, metrics.heightPixels);
				if (mSurfaceWidth == 0)
					mSurfaceWidth = Math.max(metrics.widthPixels, metrics.heightPixels);
				mGestureDector.onTouchEvent(event);
				return true;
			}
		});

		return view;
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.video, menu);
		super.onCreateOptionsMenu(menu, inflater);
		VideoPlayer player = VideoPlayerFactory.getInstance();
		if(player == null)
			return;
		if (player.getAudioTracksCount() <= 0)
			menu.removeItem(R.id.menu_audio_track);
		if (player.getSubtitleTracksCount() <= 0)
			menu.removeItem(R.id.menu_subtitle);

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_audio_track:
				onSelectAudioTrack();
				return true;
			case R.id.menu_subtitle:
				onSelectSubtitleTrack();
				return true;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void onSelectAudioTrack() {
		MediaPlayer player = VLCPlayer.getMediaPlayer();
		showTrackSelection(getString(R.string.audio_tracks), player.getAudioTracks(), DIALOG_TAG_AUDIO_TRACK);
	}

	private void onSelectSubtitleTrack() {
		MediaPlayer player = VLCPlayer.getMediaPlayer();
		showTrackSelection(getString(R.string.subtitles), player.getSpuTracks(), DIALOG_TAG_SUBTITLE_TRACK);
	}

	private void showTrackSelection(String title, MediaPlayer.TrackDescription[] descriptions, String dialog_tag) {
		//this should actually never be true, but just to be sure we do it anyways
		if (descriptions == null || descriptions.length == 0) {
			Toast.makeText(getContext(), R.string.no_tracks, Toast.LENGTH_SHORT).show();
			return;
		}
		CharSequence[] actions = new CharSequence[descriptions.length];
		int[] ids = new int[descriptions.length];
		int i = 0;
		for (MediaPlayer.TrackDescription description : descriptions) {
			actions[i] = description.name;
			ids[i] = description.id;
			i++;
		}
		SimpleChoiceDialog choice = SimpleChoiceDialog.newInstance(title, actions, ids);
		choice.show(getFragmentManager(), dialog_tag);
	}

	private void onVolumeTouch(float distance_y) {
		float delta = (distance_y / mSurfaceHeight) * 100;
		float currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / mAudioMaxVol * 100;
		if (mVolume > 0)
			currentVolume = mVolume;

		currentVolume += delta;
		currentVolume = Math.max(Math.min(currentVolume, 100f), 0f);
		mVolume = currentVolume;
		setVolume((int) (currentVolume / 100 * mAudioMaxVol));
	}

	protected void setVolume(int volume) {
		int currentVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		if (volume != currentVol)
			mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
	}

	private void onBrightnessTouch(float distance_y) {
		float delta = distance_y / mSurfaceHeight;

		Window window = getActivity().getWindow();
		WindowManager.LayoutParams layoutParams = window.getAttributes();
		layoutParams.screenBrightness = Math.min(Math.max(layoutParams.screenBrightness + delta, 0.01f), 1f);
		window.setAttributes(layoutParams);
	}


	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		onServiceInfoChanged(true);
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AbstractListRequestHandler handler;
		if (DreamDroid.featureNowNext())
			handler = new EpgNowNextListRequestHandler();
		else
			handler = new EventListRequestHandler(URIStore.EPG_NOW);
		return new AsyncListLoader(getActivity(), handler, true, args);
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader, LoaderResult<ArrayList<ExtendedHashMap>> data) {
		if (data.isError())
			return;
		mServiceList.clear();
		mServicesView.getAdapter().notifyDataSetChanged();
		mServiceList.addAll(data.getResult());
		for (ExtendedHashMap service : mServiceList) {
			if (service.getString(Event.KEY_SERVICE_REFERENCE).equals(mServiceRef)) {
				ExtendedHashMap oldServiceInfo = mServiceInfo;
				mServiceInfo = service;
				String eventid = mServiceInfo.getString(Event.KEY_EVENT_ID, "-1");
				if (!eventid.equals(oldServiceInfo.getString(Event.KEY_EVENT_ID, "-2")))
					onServiceInfoChanged(false);
			}
			mServicesView.getAdapter().notifyDataSetChanged();
		}
	}

	private boolean isServiceDetailVisible() {
		View root = getView();
		return root != null && root.findViewById(R.id.service_detail_root).getVisibility() == View.VISIBLE;
	}

	@Override
	public void onLoaderReset(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader) {

	}

	private void previous() {
		int index = getCurrentServiceIndex();
		if (index < 0)
			return;
		if (index == 0)
			index = mServiceList.size() - 1;
		else
			index--;
		mServiceInfo = mServiceList.get(index);
		mServiceRef = mServiceInfo.getString(Event.KEY_SERVICE_REFERENCE);
		mServiceName = mServiceInfo.getString(Event.KEY_SERVICE_NAME);
		zap();
	}

	private void zap() {
		if (Service.isMarker(mServiceRef))
			return;
		Intent streamingIntent = IntentFactory.getStreamServiceIntent(getActivity(), mServiceRef, mServiceName, mBouquetRef, mServiceInfo);
		getArguments().putString(TITLE, mServiceRef);
		getArguments().getString(SERVICE_REFERENCE, mServiceRef);
		getArguments().getString(BOUQUET_REFERENCE, mBouquetRef);
		getArguments().putSerializable(SERVICE_INFO, mServiceInfo);
		((VideoActivity) getActivity()).handleIntent(streamingIntent);
		onServiceInfoChanged(true);
	}

	private void next() {
		int index = getCurrentServiceIndex();
		if (index < 0)
			return;
		if (index >= mServiceList.size())
			index = 0;
		else
			index++;
		mServiceInfo = mServiceList.get(index);
		mServiceRef = mServiceInfo.getString(Event.KEY_SERVICE_REFERENCE);
		mServiceName = mServiceInfo.getString(Event.KEY_SERVICE_NAME);
		zap();
	}

	private int getCurrentServiceIndex() {
		if (mServiceList == null || mServiceList.isEmpty())
			return -1;
		int idx = 0;
		for (ExtendedHashMap service : mServiceList) {
			if (service.getString(Event.KEY_SERVICE_REFERENCE).equals(mServiceRef))
				return idx;
			idx++;
		}
		return -1;
	}

	private void onServiceInfoChanged(boolean doShowOverlay) {
		Log.d(LOG_TAG, "service info changed!");
		if (doShowOverlay)
			showOverlays(false);
		else
			updateViews();
		if (mServiceInfo == null)
			return;
		mHandler.removeCallbacks(mIssueReloadRunnable);
		//let's see if we have any info about when the current event ends
		String start = mServiceInfo.getString(Event.KEY_EVENT_START);
		String duration = mServiceInfo.getString(Event.KEY_EVENT_DURATION);
		if (duration != null && start != null && !Python.NONE.equals(duration) && !Python.NONE.equals(start)) {
			long eventStart = Double.valueOf(start).longValue() * 1000;
			long eventEnd = eventStart + (Double.valueOf(duration).longValue() * 1000);
			long now = new Date().getTime();
			long updateAt = SystemClock.uptimeMillis() + eventEnd - now;
			mHandler.postAtTime(mIssueReloadRunnable, updateAt);
		} else {
			Log.i(LOG_TAG, "No Eventinfo present, will update in 5 Minutes!");
			mHandler.postDelayed(mIssueReloadRunnable, 300000); //update in 5 minutes
		}
	}

	public void reload() {
		if ((mBouquetRef == null || mBouquetRef.isEmpty()) || getActivity() == null)
			return;
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("bRef", mBouquetRef));
		Bundle args = new Bundle();
		args.putSerializable("params", params);
		getLoaderManager().restartLoader(1, args, this);
	}

	private void seek(int pos) {
		VideoPlayer player = VideoPlayerFactory.getInstance();
		if (player == null)
			return;
		float fpos = (float) pos;

		if (player.getLength() > 0)
			fpos = fpos / player.getLength() * 100;
		player.setPosition(fpos / 100f);
	}

	private void updateViews() {
		View view = getView();
		TextView serviceName = (TextView) view.findViewById(R.id.service_name);
		serviceName.setText(mServiceName);

		View parentNow = view.findViewById(R.id.event_now);
		View parentNext = view.findViewById(R.id.event_next);

		if (mServiceInfo != null) {
			ImageView picon = (ImageView) view.findViewById(R.id.picon);
			Picon.setPiconForView(getActivity(), picon, mServiceInfo, Statics.TAG_PICON);

			TextView nowStart = (TextView) view.findViewById(R.id.event_now_start);
			TextView nowDuration = (TextView) view.findViewById(R.id.event_now_duration);
			TextView nowTitle = (TextView) view.findViewById(R.id.event_now_title);

			Event.supplementReadables(mServiceInfo); //update readable values

			nowStart.setText(mServiceInfo.getString(Event.KEY_EVENT_START_TIME_READABLE));
			nowTitle.setText(mServiceInfo.getString(Event.KEY_EVENT_TITLE));
			nowDuration.setText(mServiceInfo.getString(Event.KEY_EVENT_DURATION_READABLE));

			parentNow.setVisibility(View.VISIBLE);

			String next = mServiceInfo.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE));
			boolean hasNext = next != null && !"".equals(next);
			if (hasNext) {
				TextView nextStart = (TextView) view.findViewById(R.id.event_next_start);
				TextView nextDuration = (TextView) view.findViewById(R.id.event_next_duration);
				TextView nextTitle = (TextView) view.findViewById(R.id.event_next_title);

				nextStart.setText(mServiceInfo.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_START_TIME_READABLE)));
				nextTitle.setText(mServiceInfo.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE)));
				nextDuration.setText(mServiceInfo.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_DURATION_READABLE)));
				parentNext.setVisibility(View.VISIBLE);
			} else {
				parentNext.setVisibility(View.GONE);
			}
		} else {
			parentNow.setVisibility(View.GONE);
			parentNext.setVisibility(View.GONE);
		}
		updateProgress();
		mServicesView.getAdapter().notifyDataSetChanged();
	}

	protected void updateProgress() {
		SeekBar serviceProgress = (SeekBar) getView().findViewById(R.id.service_progress);
		VideoPlayer player = VideoPlayerFactory.getInstance();
		boolean isSeekable = player != null && player.isSeekable();
		if (isSeekable) {
			serviceProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if (fromUser) {
						seek(progress);
					}
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {

				}

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {

				}
			});
			serviceProgress.setOnTouchListener(null);
		} else {
			serviceProgress.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View view, MotionEvent motionEvent) {
					return true;
				}
			});
		}
		long max = -1;
		long cur = -1;
		if (mServiceInfo != null) {
			String duration = mServiceInfo.getString(Event.KEY_EVENT_DURATION);
			String start = mServiceInfo.getString(Event.KEY_EVENT_START);

			if (duration != null && start != null && !Python.NONE.equals(duration) && !Python.NONE.equals(start)) {
				try {
					max = Double.valueOf(duration).longValue();
					cur = max - DateTime.getRemaining(duration, start) * 60;
				} catch (Exception e) {
					Log.e(DreamDroid.LOG_TAG, e.toString());
				}
			}
		}
		if (max <= 0) {
			max = player.getLength();
			cur = (long) player.getPosition();
		}

		if (max <= 0 && isSeekable) {
			max = 100;
			cur = 0;
		}

		if (max > 0 && cur >= 0) {
			serviceProgress.setEnabled(true);
			serviceProgress.setVisibility(View.VISIBLE);
			serviceProgress.setMax((int) max);
			serviceProgress.setProgress((int) cur);
		} else {
			serviceProgress.setEnabled(false);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		showOverlays(false);
		reload();
	}

	@Override
	public void onPause() {
		mHandler.removeCallbacks(mAutoHideRunnable);
		mHandler.removeCallbacks(mIssueReloadRunnable);
		super.onPause();
	}


	public ActionBar getActionBar() {
		AppCompatActivity act = (AppCompatActivity) getActivity();
		if (act != null)
			return act.getSupportActionBar();
		return null;
	}

	public Toolbar getToolbar() {
		return (Toolbar) getActivity().findViewById(R.id.toolbar);
	}

	private void showToolbar() {
		if (getActionBar().isShowing())
			return;
		mIsHiding = false;
		ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			getActionBar().setShowHideAnimationEnabled(true);
			actionBar.show();
		}
		getToolbar().animate().translationY(0);
	}

	private void hideToolbar() {
		if (mIsHiding)
			return;
		mIsHiding = true;
		getToolbar()
				.animate()
				.translationY(-getToolbar().getHeight())
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						if (mIsHiding) {
							mIsHiding = false;
							ActionBar actionBar = getActionBar();
							if (actionBar != null) {
								getActionBar().setShowHideAnimationEnabled(true);
								actionBar.hide();
							}
						}
						super.onAnimationEnd(animation);
					}
				});
	}

	public void autohide() {
		mHandler.postDelayed(mAutoHideRunnable, AUTOHIDE_DEFAULT_TIMEOUT);
	}

	public void showOverlays(boolean doShowZapOverlays) {
		View view = getView();
		doShowZapOverlays &= !DreamDroid.isTV(getContext());
		if (view == null)
			return;
		mHandler.removeCallbacks(mAutoHideRunnable);
		updateViews();
		showToolbar();
		for (int id : sOverlayViews)
			fadeInView(view.findViewById(id));
		if (doShowZapOverlays)
			showZapOverlays();
		else
			autohide();
	}

	public void hideOverlays() {
		View view = getView();
		if (view == null)
			return;
		mHandler.removeCallbacks(mAutoHideRunnable);
		hideToolbar();
		for (int id : sOverlayViews)
			fadeOutView(view.findViewById(id));
		hideZapOverlays();
	}

	private void showZapOverlays() {
		if (mServiceList == null || mServiceList.isEmpty()) {
			hideZapOverlays();
			return;
		}
		View view = getView();
		if (view == null)
			return;
		for (int id : sZapOverlayViews)
			fadeInView(view.findViewById(id));
		autohide();
	}

	private void hideZapOverlays() {
		View view = getView();
		if (view == null)
			return;
		for (int id : sZapOverlayViews)
			fadeOutView(view.findViewById(id));
	}

	private void fadeInView(final View v) {
		if (v == null || v.getVisibility() == View.VISIBLE)
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
		if (v == null || v.getVisibility() == View.GONE)
			return;
		v.animate().alpha(0.0f).setListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				v.setVisibility(View.GONE);
			}
		});
	}

	public void toggleViews() {
		if (isOverlaysVisible())
			hideOverlays();
		else
			showOverlays(true);
	}

	protected boolean isOverlaysVisible() {
		View sdroot = getView().findViewById(R.id.service_detail_root);
		return sdroot.getVisibility() == View.VISIBLE;
	}

	@Override
	public void onEvent(MediaPlayer.Event event) {
		getActivity().supportInvalidateOptionsMenu();
		switch (event.type) {
			case MediaPlayer.Event.Opening: {
				View progressView = getView().findViewById(R.id.video_load_progress);
				fadeInView(progressView);
				break;
			}
			case MediaPlayer.Event.Playing: {
				View progressView = getView().findViewById(R.id.video_load_progress);
				fadeOutView(progressView);
				updateProgress();
				hideOverlays();
				break;
			}
			case MediaPlayer.Event.PositionChanged:
				updateProgress();
				break;
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

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		String serviceRef = mServiceList.get(position).getString(Event.KEY_SERVICE_REFERENCE);
		if (Service.isMarker(serviceRef))
			return;
		mServiceInfo = mServiceList.get(position);
		mServiceRef = serviceRef;
		mServiceName = mServiceInfo.getString(Event.KEY_SERVICE_NAME);
		zap();
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		MediaPlayer player = VLCPlayer.getMediaPlayer();
		if (DIALOG_TAG_AUDIO_TRACK.equals(dialogTag)) {
			player.setAudioTrack(action);
		} else if (DIALOG_TAG_SUBTITLE_TRACK.equals(dialogTag)) {
			player.setSpuTrack(action);
		}
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B)) {
			if (isOverlaysVisible()) {
				hideOverlays();
				return true;
			}
			return false;
		}
		if (!isOverlaysVisible()) {
			showOverlays(true);
			return true;
		}
		return false;
	}
}