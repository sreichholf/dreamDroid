package net.reichholf.dreamdroid.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
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
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class VideoOverlayFragment extends Fragment implements MediaPlayer.EventListener,
		LoaderManager.LoaderCallbacks<LoaderResult<ArrayList<ExtendedHashMap>>>, ItemClickSupport.OnItemClickListener, ActionDialog.DialogActionListener {

	public static final String DIALOG_TAG_AUDIO_TRACK = "dialog_audio_track";
	public static final String DIALOG_TAG_SUBTITLE_TRACK = "dialog_subtitle_track";

	private static final int AUTOHIDE_DEFAULT_TIMEOUT = 7000;
	private static final int sFakeLength = 10000;

	private static final String LOG_TAG = VideoOverlayFragment.class.getSimpleName();
	private final int[] sOverlayViews = {R.id.service_detail_root, R.id.epg, R.id.toolbar};
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

	protected FrameLayout mEpgView;
	protected AutofitRecyclerView mServicesView;
	protected ItemClickSupport mItemClickSupport;
	protected AppCompatImageButton mAudioTrackButton;
	protected AppCompatImageButton mSubtitleTrackButton;
	protected Button mNextButton;
	protected Button mPreviousButton;
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
		mServiceInfo = ((ExtendedHashMap) getArguments().get(SERVICE_INFO));
		mHandler = new Handler();
		mAutoHideRunnable = () -> hideOverlays();
		mIssueReloadRunnable = () -> reload();

		mAudioManager = (AudioManager) getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
		mAudioMaxVol = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		mVolume = -1f;

		autohide();
		reload();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.video_player_overlay, container, false);
		mServicesView = view.findViewById(R.id.servicelist);
		if (mServicesView != null) {
			mServicesView.setLayoutManager(new GridLayoutManager(getActivity(), 1));
			mServicesView.addItemDecoration(new SpacesItemDecoration(getActivity().getResources().getDimensionPixelSize(R.dimen.recylcerview_content_margin)));
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
		}
		mEpgView = view.findViewById(R.id.epg);

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

		view.findViewById(R.id.overlay_root).setOnTouchListener((v, event) -> {
			DisplayMetrics metrics = new DisplayMetrics();
			getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
			if (mSurfaceHeight == 0)
				mSurfaceHeight = Math.min(metrics.widthPixels, metrics.heightPixels);
			if (mSurfaceWidth == 0)
				mSurfaceWidth = Math.max(metrics.widthPixels, metrics.heightPixels);
			mGestureDector.onTouchEvent(event);
			return true;
		});
		mAudioTrackButton = view.findViewById(R.id.button_audio_track);
		mAudioTrackButton.setOnClickListener(v -> onSelectAudioTrack());
		mSubtitleTrackButton = view.findViewById(R.id.button_subtitle_track);
		mSubtitleTrackButton.setOnClickListener(v -> onSelectSubtitleTrack());

		mPreviousButton = view.findViewById(R.id.button_previous);
		mPreviousButton.setOnClickListener(v -> previous());
		mNextButton = view.findViewById(R.id.button_next);
		mNextButton.setOnClickListener(v -> next());

		return view;
	}


	public void onUpdateButtons() {
		VideoPlayer player = VideoPlayerFactory.getInstance();
		if(player == null)
			return;
		if (player.getAudioTracksCount() <= 0)
			mAudioTrackButton.setVisibility(View.INVISIBLE);
		else
			mAudioTrackButton.setVisibility(View.VISIBLE);
		if (player.getSubtitleTracksCount() <= 0)
			mSubtitleTrackButton.setVisibility(View.INVISIBLE);
		else
			mSubtitleTrackButton.setVisibility(View.VISIBLE);
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
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		onServiceInfoChanged(true);
	}

	@NonNull
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
	public void onLoadFinished(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader, LoaderResult<ArrayList<ExtendedHashMap>> data) {
		if (data.isError())
			return;
		mServiceList.clear();
		if (mServicesView != null)
			mServicesView.getAdapter().notifyDataSetChanged();
		mServiceList.addAll(data.getResult());
		for (ExtendedHashMap service : mServiceList) {
			if (service.getString(Event.KEY_SERVICE_REFERENCE).equals(mServiceRef)) {
				ExtendedHashMap oldServiceInfo = mServiceInfo;
				mServiceInfo = service;
				String eventid = mServiceInfo.getString(Event.KEY_EVENT_ID, "-1");
				if (oldServiceInfo == null || !eventid.equals(oldServiceInfo.getString(Event.KEY_EVENT_ID, "-2")))
					onServiceInfoChanged(false);
			}
			if (mServicesView != null)
				mServicesView.getAdapter().notifyDataSetChanged();
		}
	}

	@Override
	public void onLoaderReset(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader) {

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

	private ExtendedHashMap getPreviousServiceInfo() {
		int index = getCurrentServiceIndex();
		if (index < 0)
			return null;
		if (index == 0)
			index = mServiceList.size() - 1;
		else
			index--;
		return mServiceList.get(index);
	}

	private void previous() {
		ExtendedHashMap serviceInfo = getPreviousServiceInfo();
		if (serviceInfo == null)
			return;
		mServiceInfo = serviceInfo;
		mServiceRef = mServiceInfo.getString(Event.KEY_SERVICE_REFERENCE);
		mServiceName = mServiceInfo.getString(Event.KEY_SERVICE_NAME);
		zap();
	}

	private ExtendedHashMap getNextServiceInfo() {
		int index = getCurrentServiceIndex();
		if (index < 0)
			return null;
		index++;
		if (index >= mServiceList.size()-1)
			index = 0;

		return mServiceList.get(index);
	}

	private void next() {
		ExtendedHashMap serviceInfo = getNextServiceInfo();
		if (serviceInfo == null)
			return;
		mServiceInfo = serviceInfo;
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
			long now = System.currentTimeMillis();
			long delay = eventEnd - now;
			if (eventEnd <= now)
				delay = now; //outdated, reload in few seconds
			delay += 2000;
			mHandler.postDelayed(mIssueReloadRunnable, delay);
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

		long length = player.getLength();
		if (length > 0)
			length = length / 1000;
		else
			length = sFakeLength;
		player.setPosition(fpos / length);

	}

	private boolean isRecording() {
		boolean isDreamboxRecording = mServiceInfo != null && mServiceInfo.containsKey(Movie.KEY_FILE_NAME);
		return VideoPlayerFactory.getInstance().isSeekable() || isDreamboxRecording;
	}

	private void updateViews() {
		View view = getView();
		TextView serviceName = view.findViewById(R.id.service_name);
		serviceName.setText(mServiceName);

		View parentNow = view.findViewById(R.id.event_now);
		View parentNext = view.findViewById(R.id.event_next);

		if (mServiceInfo != null) {
			if (isRecording()) {
			} else {
				TextView nowStart = view.findViewById(R.id.event_now_start);
				TextView nowDuration = view.findViewById(R.id.event_now_duration);
				TextView nowTitle = view.findViewById(R.id.event_now_title);

				ImageView picon = view.findViewById(R.id.picon);
				Picon.setPiconForView(getActivity(), picon, mServiceInfo, Statics.TAG_PICON);

				Event.supplementReadables(mServiceInfo); //update readable values

				nowStart.setText(mServiceInfo.getString(Event.KEY_EVENT_START_TIME_READABLE));
				nowTitle.setText(mServiceInfo.getString(Event.KEY_EVENT_TITLE));
				nowDuration.setText(mServiceInfo.getString(Event.KEY_EVENT_DURATION_READABLE));

				parentNow.setVisibility(View.VISIBLE);
			}
			if (mEpgView != null) {
				TextView titleView = mEpgView.findViewById(R.id.epg_title);
				TextView descriptionView = mEpgView.findViewById(R.id.epg_description);
				TextView descriptionExView = mEpgView.findViewById(R.id.epg_description_extended);
				titleView.setText(mServiceInfo.getString(Event.KEY_EVENT_TITLE));
				descriptionView.setText(mServiceInfo.getString(Event.KEY_EVENT_DESCRIPTION));
				descriptionExView.setText(mServiceInfo.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED));
			}

			String next = mServiceInfo.getString(Event.PREFIX_NEXT.concat(Event.KEY_EVENT_TITLE));
			boolean hasNext = next != null && !"".equals(next);
			if (hasNext) {
				TextView nextStart = view.findViewById(R.id.event_next_start);
				TextView nextDuration = view.findViewById(R.id.event_next_duration);
				TextView nextTitle = view.findViewById(R.id.event_next_title);

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
		if (mServicesView != null)
			mServicesView.getAdapter().notifyDataSetChanged();
		ExtendedHashMap prevService = getPreviousServiceInfo();
		if (prevService != null) {
			mPreviousButton.setVisibility(View.VISIBLE);
			mPreviousButton.setText(prevService.getString(Event.KEY_SERVICE_NAME, ""));
		} else {
			mPreviousButton.setVisibility(View.INVISIBLE);
		}
		ExtendedHashMap nextService = getNextServiceInfo();
		if (nextService != null) {
			mNextButton.setVisibility(View.VISIBLE);
			mNextButton.setText(nextService.getString(Event.KEY_SERVICE_NAME, ""));
		} else
			mNextButton.setVisibility(View.INVISIBLE);

	}

	@SuppressLint("ClickableViewAccessibility")
	protected void updateProgress() {
		SeekBar serviceProgress = getView().findViewById(R.id.service_progress);
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
			serviceProgress.setOnTouchListener((view, motionEvent) -> false);
		} else {
			serviceProgress.setOnTouchListener((view, motionEvent) -> true);
		}
		serviceProgress.setFocusable(isSeekable);
		serviceProgress.setClickable(isSeekable);
		long len = -1;
		long cur = -1;
		if (mServiceInfo != null) {
			View parentNow = getView().findViewById(R.id.event_now);
			View parentNext = getView().findViewById(R.id.event_next);
			if (isRecording()) {
				long duration = player.getLength() / 1000;
				if (duration <= 0) {
					String textLen = mServiceInfo.getString(Movie.KEY_LENGTH, "00:00");
					String[] l = textLen.split(":");
					try {
						duration = (Long.valueOf(l[0]) * 60) + Long.valueOf(l[2]);
					} catch (NumberFormatException nex) {
						Log.w(LOG_TAG, nex.getLocalizedMessage());
					} catch (IndexOutOfBoundsException iobex) {
						Log.w(LOG_TAG, iobex.getLocalizedMessage());
					}
				}
				if (duration > 0) {
					TextView nowStart = getView().findViewById(R.id.event_now_start);
					TextView nowDuration = getView().findViewById(R.id.event_now_duration);
					TextView nowTitle = getView().findViewById(R.id.event_now_title);

					long pos = (long) (duration * player.getPosition()); //getTime() may deliver quite bogous values when streaming from a dreambox so we don't use them.
					nowStart.setText(DateTime.minutesAndSeconds((int) pos));
					nowTitle.setText(mServiceInfo.getString(Movie.KEY_SERVICE_NAME, ""));
					nowDuration.setText(DateTime.minutesAndSeconds((int) duration));
					parentNow.setVisibility(View.VISIBLE);
				} else {
					parentNow.setVisibility(View.GONE);
				}
				parentNext.setVisibility(View.GONE);
			} else {
				String duration = mServiceInfo.getString(Event.KEY_EVENT_DURATION);
				String start = mServiceInfo.getString(Event.KEY_EVENT_START);

				if (duration != null && start != null && !Python.NONE.equals(duration) && !Python.NONE.equals(start)) {
					try {
						len = Double.valueOf(duration).longValue();
						cur = len - DateTime.getRemaining(duration, start) * 60;
					} catch (Exception e) {
						Log.e(DreamDroid.LOG_TAG, e.toString());
					}
				}
			}
		}
		if (len <= 0) {
			len = player.getLength() / 1000; //ms -> sec
			cur = player.getTime() / 1000; //ms -> sec
		}

		if (len <= 0 && isSeekable) {
			len = sFakeLength;
			cur = (long) (len * player.getPosition());
		}

		if (len > 0 && cur >= 0) {
			serviceProgress.setEnabled(true);
			serviceProgress.setVisibility(View.VISIBLE);
			serviceProgress.setMax((int) len);
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
		if (v.getId() == R.id.epg && isRecording())
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
		boolean ret = false;
		mHandler.removeCallbacks(mAutoHideRunnable);
		autohide();
		switch(keyCode) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_BUTTON_B:
				if (isOverlaysVisible()) {
					hideOverlays();
					ret = true;
				} else
					return false;
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (isOverlaysVisible())
					return false;
				previous();
				ret = true;
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (isOverlaysVisible())
					return false;
				next();
				ret = true;
				break;
		}
		if (!isOverlaysVisible()) {
			showOverlays(true);
			ret = true;
		}
		return ret;
	}
}