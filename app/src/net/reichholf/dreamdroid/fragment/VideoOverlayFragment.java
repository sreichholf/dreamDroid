package net.reichholf.dreamdroid.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.HorizontalGridView;
import androidx.preference.PreferenceManager;

import kotlinx.coroutines.Job;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.VideoActivity;
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.fragment.dialogs.MovieDetailBottomSheet;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.enigma.EpgNowNextLoadKt;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.ServiceNowNext;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.ui.services.MovieListMapperKt;
import net.reichholf.dreamdroid.ui.services.ServiceListMapperKt;
import net.reichholf.dreamdroid.tv.fragment.EpgDetailDialog;
import net.reichholf.dreamdroid.tv.fragment.MovieDetailDialog;
import net.reichholf.dreamdroid.ui.video.VideoOverlayScreenKt;
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState;
import net.reichholf.dreamdroid.video.VLCPlayer;
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport;
import net.reichholf.dreamdroid.widget.helper.SpacesItemDecoration;

import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;

public class VideoOverlayFragment extends Fragment implements MediaPlayer.EventListener,
		ItemClickSupport.OnItemClickListener, ActionDialog.DialogActionListener {

	public static final String DIALOG_TAG_AUDIO_TRACK = "dialog_audio_track";
	public static final String DIALOG_TAG_SUBTITLE_TRACK = "dialog_subtitle_track";

	private static final int AUTOHIDE_DEFAULT_TIMEOUT = 7000;
	private static final int sFakeLength = 10000;

	private static final String LOG_TAG = VideoOverlayFragment.class.getSimpleName();
	static float sOverlayAlpha = 0.85f;
	static float sSeekStepSize = 0.02f;

	public final String TITLE = "title";
	public final String SERVICE_INFO = "serviceInfo";
	public final String BOUQUET_REFERENCE = "bouquetRef";
	public final String SERVICE_REFERENCE = "serviceRef";

	protected int mSurfaceHeight;
	protected int mSurfaceWidth;

	@Nullable
	protected String mTitle;
	@Nullable
	protected String mServiceRef;
	protected String mBouquetRef;

	protected ArrayList<ServiceNowNext> mServiceList;
	@Nullable
	protected ServiceNowNext mCurrentService;
	@Nullable
	protected net.reichholf.dreamdroid.enigma.Movie mMovie;

	protected Handler mHandler;
	protected Runnable mAutoHideRunnable;
	protected Runnable mIssueReloadRunnable;

    protected ItemClickSupport mItemClickSupport;

	@Nullable
	protected View mOverlayRoot;

	@Nullable
	protected RecyclerView mServicesView;

	@Nullable
	protected ComposeView mComposeOverlay;

	@NonNull
	protected final VideoOverlayUiState mOverlayUiState = new VideoOverlayUiState();

	@Nullable
	private GestureDetectorCompat mGestureDector;
	private AudioManager mAudioManager;
	private int mAudioMaxVol;
	private float mVolume;
	private boolean mServicesViewVisible;

	@Nullable
	private Job mLoadJob;

	public VideoOverlayFragment() {
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		setRetainInstance(true);
		setHasOptionsMenu(true);
		super.onCreate(savedInstanceState);
		mTitle = getArguments().getString(TITLE);
		mServiceRef = getArguments().getString(SERVICE_REFERENCE);
		mBouquetRef = getArguments().getString(BOUQUET_REFERENCE);
		mServiceList = new ArrayList<>();
		ExtendedHashMap serviceInfoHash = (ExtendedHashMap) getArguments().get(SERVICE_INFO);
		if (serviceInfoHash != null) {
			if (serviceInfoHash.containsKey(Movie.KEY_FILE_NAME)) {
				mMovie = MovieListMapperKt.movieFromExtendedHashMap(serviceInfoHash);
			} else {
				mCurrentService = ServiceListMapperKt.serviceNowNextFromExtendedHashMap(serviceInfoHash);
			}
		}
		mHandler = new Handler();
		mServicesViewVisible = false;
		mAutoHideRunnable = () -> hideOverlays();
		mIssueReloadRunnable = () -> reload();

		mAudioManager = (AudioManager) getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
		mAudioMaxVol = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		mVolume = -1f;

		autohide();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.video_player_overlay, container, false);
		mOverlayRoot = view.findViewById(R.id.overlay_root);
		mServicesView = view.findViewById(R.id.servicelist);
		mComposeOverlay = view.findViewById(R.id.compose_overlay);
		VideoOverlayScreenKt.bindVideoOverlayScreen(
				mComposeOverlay,
				mOverlayUiState,
				() -> {
					onPlay();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onRewind();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onForward();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onInfo();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onList();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onSelectAudioTrack();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onSelectSubtitleTrack();
					return kotlin.Unit.INSTANCE;
				},
				progress -> {
					seek(progress);
					return kotlin.Unit.INSTANCE;
				}
		);
		return view;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if (mServicesView != null) {
			if (DreamDroid.isTV(getContext())) {
				HorizontalGridView gridView = (HorizontalGridView) mServicesView;
				gridView.setNumRows(1);
			} else {
				GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), 1);
				mServicesView.setLayoutManager(layoutManager);
			}
			if (mServiceList.isEmpty())
				mOverlayUiState.setShowListButton(false);
			mServicesView.addItemDecoration(new SpacesItemDecoration(getActivity().getResources().getDimensionPixelSize(R.dimen.recylcerview_content_margin)));
			mItemClickSupport = ItemClickSupport.addTo(mServicesView);
			mItemClickSupport.setOnItemClickListener(this);

			ServiceAdapter adapter = new ServiceAdapter(getActivity(), mServiceList);
			mServicesView.setAdapter(adapter);
			mServicesView.addOnScrollListener(new RecyclerView.OnScrollListener() {
				@Override
				public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
					super.onScrollStateChanged(recyclerView, newState);
					if (newState == RecyclerView.SCROLL_STATE_IDLE)
						autohide();
					else
						mHandler.removeCallbacks(mAutoHideRunnable);
				}
			});
			mServicesViewVisible = mServicesView.getVisibility() == View.VISIBLE;
		}

		mGestureDector = new GestureDetectorCompat(getActivity(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
				boolean isGesturesEnabled = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(DreamDroid.PREFS_KEY_VIDEO_ENABLE_GESTURES, true);
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

		getActivity().findViewById(R.id.overlay).setOnTouchListener((v, event) -> {
			DisplayMetrics metrics = new DisplayMetrics();
			getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
			if (mSurfaceHeight == 0)
				mSurfaceHeight = Math.min(metrics.widthPixels, metrics.heightPixels);
			if (mSurfaceWidth == 0)
				mSurfaceWidth = Math.max(metrics.widthPixels, metrics.heightPixels);
			mGestureDector.onTouchEvent(event);
			return true;
		});
	}

	protected void onRewind() {
		VLCPlayer p = VLCPlayer.get();
		p.setPosition(Math.max(0.0f, p.getPosition() - sSeekStepSize));
		autohide();
	}

	protected void onForward() {
		VLCPlayer p = VLCPlayer.get();
		p.setPosition(Math.max(0.0f, p.getPosition() + sSeekStepSize));
		autohide();
	}

	protected void onPlay() {
		VLCPlayer.get().play();
		autohide();
	}

	public void onUpdateButtons() {
		VLCPlayer player = VLCPlayer.get();
		if (player == null)
			return;
		mOverlayUiState.setShowAudioButton(player.getAudioTracksCount() > 0);
		mOverlayUiState.setShowSubtitleButton(player.getSubtitleTracksCount() > 0);
	}

	private void onSelectAudioTrack() {
		MediaPlayer player = VLCPlayer.getMediaPlayer();
		showTrackSelection(getString(R.string.audio_tracks), player.getAudioTracks(), DIALOG_TAG_AUDIO_TRACK);
	}

	private void onSelectSubtitleTrack() {
		MediaPlayer player = VLCPlayer.getMediaPlayer();
		showTrackSelection(getString(R.string.subtitles), player.getSpuTracks(), DIALOG_TAG_SUBTITLE_TRACK);
	}

	private void onInfo(){
		if (mMovie == null && mCurrentService == null)
			return;

		DialogFragment detailDialog;
		if (mMovie != null) {
			if (DreamDroid.isTV(getContext()))
				detailDialog = MovieDetailDialog.newInstance(mMovie);
			else
				detailDialog = MovieDetailBottomSheet.newInstance(mMovie);
		} else {
			Event event = mCurrentService.getNow();
			if (event == null) {
				event = new Event(
						"", "", "", "", "", "", "",
						mCurrentService.getServiceReference(),
						mCurrentService.getServiceName(),
						"", "", ""
				);
			}
			if (DreamDroid.isTV(getContext()))
				detailDialog = EpgDetailDialog.newInstance(event);
			else
				detailDialog = EpgDetailBottomSheet.newInstance(event);
		}
		if (detailDialog != null)
			detailDialog.show(getFragmentManager(), "details_dialog_tv");
	}

	private void onList() {
		if (!mServicesViewVisible) {
			mServicesViewVisible = true;
			showZapOverlays();
		} else {
			hideZapOverlays();
			mServicesViewVisible = false;
		}
	}

	private void showTrackSelection(String title, @Nullable MediaPlayer.TrackDescription[] descriptions, String dialog_tag) {
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

	private void applyServiceList(@NonNull ArrayList<ServiceNowNext> services) {
		mServiceList.clear();
		if (mServicesView != null)
			mServicesView.getAdapter().notifyDataSetChanged();
		mServiceList.addAll(services);
		for (ServiceNowNext service : mServiceList) {
			if (service.getServiceReference().equals(mServiceRef)) {
				ServiceNowNext oldService = mCurrentService;
				mCurrentService = service;
				mMovie = null;
				String eventid = mCurrentService.getNow() != null
						? mCurrentService.getNow().getEventId() : "-1";
				String oldEventId = oldService != null && oldService.getNow() != null
						? oldService.getNow().getEventId() : "-2";
				if (oldService == null || !eventid.equals(oldEventId))
					onServiceInfoChanged(false);
			}
			if (mServicesView != null)
				mServicesView.getAdapter().notifyDataSetChanged();
		}
		if (mServiceList.isEmpty()) {
			mOverlayUiState.setShowListButton(false);
			hideZapOverlays();
		} else {
			mOverlayUiState.setShowListButton(true);
			if (isOverlaysVisible() && mServicesViewVisible)
				showZapOverlays();
		}
	}

	private void zap() {
		if (Service.isMarker(mServiceRef))
			return;
		ExtendedHashMap serviceInfoHash = serviceInfoForIntent();
		Intent streamingIntent = IntentFactory.getStreamServiceIntent(getActivity(), mServiceRef, mTitle, mBouquetRef, serviceInfoHash);
		getArguments().putString(TITLE, mTitle);
		getArguments().getString(SERVICE_REFERENCE, mServiceRef);
		getArguments().getString(BOUQUET_REFERENCE, mBouquetRef);
		getArguments().putSerializable(SERVICE_INFO, serviceInfoHash);
		((VideoActivity) getActivity()).handleIntent(streamingIntent);

		onServiceInfoChanged(true);
	}

	@Nullable
	private ExtendedHashMap serviceInfoForIntent() {
		if (mMovie != null) {
			return MovieListMapperKt.movieToExtendedHashMap(mMovie);
		}
		if (mCurrentService != null) {
			return ServiceListMapperKt.serviceNowNextToExtendedHashMap(mCurrentService);
		}
		return null;
	}

	@Nullable
	private ServiceNowNext getPreviousServiceInfo() {
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
		ServiceNowNext serviceInfo = getPreviousServiceInfo();
		if (serviceInfo == null)
			return;
		mCurrentService = serviceInfo;
		mMovie = null;
		mServiceRef = mCurrentService.getServiceReference();
		mTitle = mCurrentService.getServiceName();
		zap();
	}

	@Nullable
	private ServiceNowNext getNextServiceInfo() {
		int index = getCurrentServiceIndex();
		if (index < 0)
			return null;
		index++;
		if (index >= mServiceList.size()-1)
			index = 0;

		return mServiceList.get(index);
	}

	private void next() {
		ServiceNowNext serviceInfo = getNextServiceInfo();
		if (serviceInfo == null)
			return;
		mCurrentService = serviceInfo;
		mMovie = null;
		mServiceRef = mCurrentService.getServiceReference();
		mTitle = mCurrentService.getServiceName();
		zap();
	}

	private int getCurrentServiceIndex() {
		if (mServiceList == null || mServiceList.isEmpty())
			return -1;
		int idx = 0;
		for (ServiceNowNext service : mServiceList) {
			if (service.getServiceReference().equals(mServiceRef))
				return idx;
			idx++;
		}
		return -1;
	}

	private void onServiceInfoChanged(boolean doShowOverlay) {
		Log.d(LOG_TAG, "service info changed!");
		if (doShowOverlay)
			showOverlays();
		else
			updateViews();
		if (mCurrentService == null && mMovie == null)
			return;
		mHandler.removeCallbacks(mIssueReloadRunnable);
		//let's see if we have any info about when the current event ends
		Event now = mCurrentService != null ? mCurrentService.getNow() : null;
		String start = now != null ? now.getStart() : null;
		String duration = now != null ? now.getDuration() : null;
		if (duration != null && start != null && !duration.isEmpty() && !start.isEmpty()
				&& !Python.NONE.equals(duration) && !Python.NONE.equals(start)) {
			long eventStart = Double.valueOf(start).longValue() * 1000;
			long eventEnd = eventStart + (Double.valueOf(duration).longValue() * 1000);
			long nowMs = System.currentTimeMillis();
			long delay = eventEnd - nowMs;
			if (eventEnd <= nowMs)
				delay = nowMs; //outdated, reload in few seconds
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
		if (!isAdded() || getView() == null)
			return;
		cancelLoad();
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("bRef", mBouquetRef));
		mLoadJob = EpgNowNextLoadKt.launchEpgNowNextLoad(this, params, (success, rows, errorText) -> {
			onEpgNowNextReady(success, rows, errorText);
			return kotlin.Unit.INSTANCE;
		});
	}

	private void cancelLoad() {
		if (mLoadJob != null) {
			mLoadJob.cancel(null);
			mLoadJob = null;
		}
	}

	private void onEpgNowNextReady(boolean success, @NonNull java.util.List<ServiceNowNext> rows,
			@Nullable String errorText) {
		if (!isAdded()) {
			return;
		}
		if (!success) {
			return;
		}
		ArrayList<ServiceNowNext> services = new ArrayList<>(rows);
		applyServiceList(services);
	}

	private void seek(int pos) {
		VLCPlayer player = VLCPlayer.get();
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
		boolean isDreamboxRecording = mMovie != null;
		return VLCPlayer.get().isSeekable() || isDreamboxRecording;
	}

	private void updateViews() {
		View view = getView();
		if (view == null)
			return;

		mOverlayUiState.setTitle(mTitle != null ? mTitle : "");
		VLCPlayer player = VLCPlayer.get();
		mOverlayUiState.setShowPvrControls(player != null && player.isSeekable());

		if (mMovie != null || mCurrentService != null) {
			mOverlayUiState.setShowInfoButton(true);
			if (isRecording()) {
				String movieTitle = mMovie != null ? mMovie.getTitle() : null;
				mOverlayUiState.setTitle(movieTitle != null && !movieTitle.isEmpty() ? movieTitle : (mTitle != null ? mTitle : ""));
			} else if (mCurrentService != null) {
				String serviceName = mCurrentService.getServiceName();
				mOverlayUiState.setTitle(serviceName != null && !serviceName.isEmpty() ? serviceName : (mTitle != null ? mTitle : ""));
				Event now = mCurrentService.getNow();
				mOverlayUiState.setNowStart(now != null && now.getStartTimeReadable() != null ? now.getStartTimeReadable() : "");
				mOverlayUiState.setNowTitle(now != null && now.getTitle() != null ? now.getTitle() : "");
				mOverlayUiState.setNowDuration(now != null && now.getDurationReadable() != null ? now.getDurationReadable() : "");
				mOverlayUiState.setShowNow(true);
			}

			Event nextEvent = mCurrentService != null ? mCurrentService.getNext() : null;
			String next = nextEvent != null ? nextEvent.getTitle() : null;
			boolean hasNext = next != null && !"".equals(next);
			if (hasNext) {
				mOverlayUiState.setNextStart(nextEvent.getStartTimeReadable() != null ? nextEvent.getStartTimeReadable() : "");
				mOverlayUiState.setNextTitle(nextEvent.getTitle() != null ? nextEvent.getTitle() : "");
				mOverlayUiState.setNextDuration(nextEvent.getDurationReadable() != null ? nextEvent.getDurationReadable() : "");
				mOverlayUiState.setHasNext(true);
			} else {
				mOverlayUiState.setHasNext(false);
			}
		} else {
			mOverlayUiState.setShowNow(false);
			mOverlayUiState.setHasNext(false);
			mOverlayUiState.setShowInfoButton(false);
		}
		updateProgress();
		if (mServicesView != null && mServicesView.getAdapter() != null)
			mServicesView.getAdapter().notifyDataSetChanged();
	}

	@SuppressLint("ClickableViewAccessibility")
	protected void updateProgress() {
		if (getView() == null)
			return;
		VLCPlayer player = VLCPlayer.get();
		boolean isSeekable = player != null && player.isSeekable();
		mOverlayUiState.setSeekable(isSeekable);
		long len = -1;
		long cur = -1;
		if (mMovie != null || mCurrentService != null) {
			if (isRecording()) {
				long duration = player != null ? player.getLength() / 1000 : 0;
				if (duration <= 0) {
					String textLen = mMovie != null && mMovie.getLength() != null && !mMovie.getLength().isEmpty()
							? mMovie.getLength() : "00:00";
					String[] l = textLen.split(":");
					try {
						duration = (Long.valueOf(l[0]) * 60) + Long.valueOf(l[1]);
					} catch (NumberFormatException nex) {
						Log.w(LOG_TAG, nex.getLocalizedMessage());
					} catch (IndexOutOfBoundsException iobex) {
						Log.w(LOG_TAG, iobex.getLocalizedMessage());
					}
				}
				if (duration > 0 && player != null) {
					long pos = (long) (duration * player.getPosition());
					mOverlayUiState.setNowStart(DateTime.minutesAndSeconds((int) pos));
					mOverlayUiState.setNowTitle(mMovie != null && mMovie.getServiceName() != null ? mMovie.getServiceName() : "");
					mOverlayUiState.setNowDuration(DateTime.minutesAndSeconds((int) duration));
					mOverlayUiState.setShowNow(true);
				} else {
					mOverlayUiState.setShowNow(false);
				}
				mOverlayUiState.setHasNext(false);
			} else if (mCurrentService != null && mCurrentService.getNow() != null) {
				Event now = mCurrentService.getNow();
				String duration = now.getDuration();
				String start = now.getStart();

				if (duration != null && start != null && !duration.isEmpty() && !start.isEmpty()
						&& !Python.NONE.equals(duration) && !Python.NONE.equals(start)) {
					try {
						len = Double.valueOf(duration).longValue();
						cur = len - DateTime.getRemaining(duration, start) * 60;
					} catch (Exception e) {
						Log.e(DreamDroid.LOG_TAG, e.toString());
					}
				}
			}
		}
		if (player != null && len <= 0) {
			len = player.getLength() / 1000; //ms -> sec
			cur = player.getTime() / 1000; //ms -> sec
		}

		if (player != null && len <= 0 && isSeekable) {
			len = sFakeLength;
			cur = (long) (len * player.getPosition());
		}

		if (len > 0 && cur >= 0) {
			mOverlayUiState.setProgressEnabled(true);
			mOverlayUiState.setProgressMax((int) len);
			mOverlayUiState.setProgress((int) cur);
		} else {
			mOverlayUiState.setProgressEnabled(false);
			mOverlayUiState.setProgressMax(0);
			mOverlayUiState.setProgress(0);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		showOverlays();
		reload();
	}

	@Override
	public void onPause() {
		mHandler.removeCallbacks(mAutoHideRunnable);
		mHandler.removeCallbacks(mIssueReloadRunnable);
		cancelLoad();
		super.onPause();
	}


	public void autohide() {
		mHandler.removeCallbacks(mAutoHideRunnable);
		mHandler.postDelayed(mAutoHideRunnable, AUTOHIDE_DEFAULT_TIMEOUT);
	}

	@Override
	public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode);
		hideOverlays();
	}

	public void showOverlays() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && getActivity().isInPictureInPictureMode()) {
			hideOverlays();
			return;
		}
		View view = getView();
		if (view == null)
			return;
		mHandler.removeCallbacks(mAutoHideRunnable);
		updateViews();
		if (mServicesViewVisible)
			showZapOverlays();
		fadeInView(mOverlayRoot);
		autohide();
	}

	public void hideOverlays() {
		View view = getView();
		if (view == null)
			return;
		mHandler.removeCallbacks(mAutoHideRunnable);
		hideZapOverlays();
		fadeOutView(mOverlayRoot);
	}

	private void showZapOverlays() {
		if (mServiceList == null || mServiceList.isEmpty()) {
			hideZapOverlays();
			return;
		}
		View view = getView();
		if (view == null)
			return;
		if (mServicesView != null) {
			mServicesView.getLayoutManager().scrollToPosition(getCurrentServiceIndex());
			fadeInView(mServicesView);
		}
		autohide();
	}

	private void hideZapOverlays() {
		View view = getView();
		if (view == null)
			return;
		fadeOutView(mServicesView);
	}

	private void fadeInView(@Nullable final View v) {
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

	private void fadeOutView(@Nullable final View v) {
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
			showOverlays();
	}

	protected boolean isOverlaysVisible() {
		View sdroot = getView().findViewById(R.id.overlay_root);
		return sdroot.getVisibility() == View.VISIBLE;
	}

	@Override
	public void onEvent(@NonNull MediaPlayer.Event event) {
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
		ServiceNowNext row = mServiceList.get(position);
		String serviceRef = row.getServiceReference();
		if (Service.isMarker(serviceRef))
			return;
		mCurrentService = row;
		mMovie = null;
		mServiceRef = serviceRef;
		mTitle = row.getServiceName();
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
		autohide();
		VLCPlayer player = VLCPlayer.get();
		switch(keyCode) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_BUTTON_B:
				if (isOverlaysVisible()) {
					hideOverlays();
					ret = true;
				} else {
					return false;
				}
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (isOverlaysVisible())
					return false;
				if (isRecording()) {
					player.slower();
					return true;
				} else
					previous();
				ret = true;
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (isOverlaysVisible())
					return false;
				if (isRecording())
					onRewind();
				else
					next();
				ret = true;
				break;
			case KeyEvent.KEYCODE_R:
			case KeyEvent.KEYCODE_MEDIA_REWIND:
				if (isRecording()) {
					onRewind();
					ret = true;
				}
				break;
			case KeyEvent.KEYCODE_F:
			case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
				if (isRecording()) {
					onForward();
					ret = true;
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_PLAY:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				player.play();
				ret = true;
		}
		if (!isOverlaysVisible()) {
			showOverlays();
			ret = true;
		}
		return ret;
	}
}