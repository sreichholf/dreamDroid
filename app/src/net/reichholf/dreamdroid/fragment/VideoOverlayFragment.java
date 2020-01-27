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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.HorizontalGridView;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;
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
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EpgNowNextListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.tv.fragment.EpgDetailDialog;
import net.reichholf.dreamdroid.tv.fragment.MovieDetailDialog;
import net.reichholf.dreamdroid.video.VLCPlayer;
import net.reichholf.dreamdroid.view.OnRepeatListener;
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport;
import net.reichholf.dreamdroid.widget.helper.SpacesItemDecoration;

import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class VideoOverlayFragment extends Fragment implements MediaPlayer.EventListener,
		LoaderManager.LoaderCallbacks<LoaderResult<ArrayList<ExtendedHashMap>>>, ItemClickSupport.OnItemClickListener, ActionDialog.DialogActionListener {

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

	protected String mTitle;
	protected String mServiceRef;
	protected String mBouquetRef;

	protected ArrayList<ExtendedHashMap> mServiceList;
	protected ExtendedHashMap mServiceInfo;

	protected Handler mHandler;
	protected Runnable mAutoHideRunnable;
	protected Runnable mIssueReloadRunnable;

    protected ItemClickSupport mItemClickSupport;

    @BindView(R.id.overlay_root)
    protected View mOverlayRoot;

	@BindView(R.id.servicelist)
	protected RecyclerView mServicesView;

    @BindView(R.id.button_audio_track)
	protected AppCompatImageButton mButtonAudioTrack;

    @BindView(R.id.button_info)
	protected AppCompatImageButton mButtonInfo;

    @BindView(R.id.button_list)
	protected AppCompatImageButton mButtonList;

    @BindView(R.id.button_subtitle_track)
    protected AppCompatImageButton mButtonSubtitleTrack;

    @BindView(R.id.button_rwd)
	protected AppCompatImageButton mButtonRewind;

    @BindView(R.id.button_play)
	protected AppCompatImageButton mButtonPlay;

    @BindView(R.id.button_fwd)
	protected AppCompatImageButton mButtonForward;

	private GestureDetectorCompat mGestureDector;
	private AudioManager mAudioManager;
	private int mAudioMaxVol;
	private float mVolume;
	private boolean mServicesViewVisible;

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
		mServiceInfo = ((ExtendedHashMap) getArguments().get(SERVICE_INFO));
		mHandler = new Handler();
		mServicesViewVisible = false;
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
		ButterKnife.bind(this, view);
		mButtonAudioTrack.setOnClickListener(v -> onSelectAudioTrack());
		mButtonInfo.setOnClickListener(v -> onInfo());
		mButtonList.setOnClickListener(v -> onList());
		mButtonSubtitleTrack.setOnClickListener(v -> onSelectSubtitleTrack());
		mButtonRewind.setOnTouchListener(new OnRepeatListener(v -> onRewind()));
		mButtonPlay.setOnClickListener(v -> onPlay());
		mButtonForward.setOnTouchListener(new OnRepeatListener(v -> onForward()));

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
				mButtonList.setVisibility(View.GONE);
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
			mServicesViewVisible = mServicesView.getVisibility() == View.VISIBLE;
		}

		mGestureDector = new GestureDetectorCompat(getActivity(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
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
		if(player == null)
			return;
		if (player.getAudioTracksCount() <= 0)
			mButtonAudioTrack.setVisibility(View.GONE);
		else
			mButtonAudioTrack.setVisibility(View.VISIBLE);
		if (player.getSubtitleTracksCount() <= 0)
			mButtonSubtitleTrack.setVisibility(View.GONE);
		else
			mButtonSubtitleTrack.setVisibility(View.VISIBLE);
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
		if (mServiceInfo == null)
			return;

		DialogFragment detailDialog;
		if (mServiceInfo.containsKey(Movie.KEY_FILE_NAME)) {

			Movie movie = new Movie(mServiceInfo);
			if (DreamDroid.isTV(getContext()))
				detailDialog = MovieDetailDialog.newInstance(movie);
			else
				detailDialog = MovieDetailBottomSheet.newInstance(movie);

		} else {
			if(DreamDroid.isTV(getContext()))
				detailDialog = EpgDetailDialog.newInstance(new Event(mServiceInfo));
			else
				detailDialog = EpgDetailBottomSheet.newInstance(mServiceInfo);
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
		if (mServiceList.isEmpty()) {
			mButtonList.setVisibility(View.GONE);
			hideZapOverlays();
		} else {
			mButtonList.setVisibility(View.VISIBLE);
			if (isOverlaysVisible() && mServicesViewVisible)
				showZapOverlays();
		}
	}

	@Override
	public void onLoaderReset(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader) {

	}

	private void zap() {
		if (Service.isMarker(mServiceRef))
			return;
		Intent streamingIntent = IntentFactory.getStreamServiceIntent(getActivity(), mServiceRef, mTitle, mBouquetRef, mServiceInfo);
		getArguments().putString(TITLE, mTitle);
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
		mTitle = mServiceInfo.getString(Event.KEY_SERVICE_NAME);
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
		mTitle = mServiceInfo.getString(Event.KEY_SERVICE_NAME);
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
			showOverlays();
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
		boolean isDreamboxRecording = mServiceInfo != null && mServiceInfo.containsKey(Movie.KEY_FILE_NAME);
		return VLCPlayer.get().isSeekable() || isDreamboxRecording;
	}

	private void updateViews() {
		View view = getView();

		TextView title = view.findViewById(R.id.title);
		title.setText(mTitle);

		if (VLCPlayer.get().isSeekable())
			view.findViewById(R.id.pvr_controls).setVisibility(View.VISIBLE);
		else
			view.findViewById(R.id.pvr_controls).setVisibility(View.GONE);

		View parentNow = view.findViewById(R.id.event_now);
		View parentNext = view.findViewById(R.id.event_next);

		if (mServiceInfo != null) {
			mButtonInfo.setVisibility(View.VISIBLE);
			if (isRecording()) {
				title.setText(mServiceInfo.getString(Movie.KEY_TITLE, mTitle));
			} else {
				title.setText(mServiceInfo.getString(Event.KEY_SERVICE_NAME, mTitle));
				TextView nowStart = view.findViewById(R.id.event_now_start);
				TextView nowDuration = view.findViewById(R.id.event_now_duration);
				TextView nowTitle = view.findViewById(R.id.event_now_title);

				Event.supplementReadables(mServiceInfo); //update readable values

				nowStart.setText(mServiceInfo.getString(Event.KEY_EVENT_START_TIME_READABLE));
				nowTitle.setText(mServiceInfo.getString(Event.KEY_EVENT_TITLE));
				nowDuration.setText(mServiceInfo.getString(Event.KEY_EVENT_DURATION_READABLE));

				parentNow.setVisibility(View.VISIBLE);
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
			mButtonInfo.setVisibility(View.GONE);
		}
		updateProgress();
		if (mServicesView != null && mServicesView.getAdapter() != null)
			mServicesView.getAdapter().notifyDataSetChanged();
	}

	@SuppressLint("ClickableViewAccessibility")
	protected void updateProgress() {
		SeekBar serviceProgress = getView().findViewById(R.id.service_progress);
		VLCPlayer player = VLCPlayer.get();
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
						duration = (Long.valueOf(l[0]) * 60) + Long.valueOf(l[1]);
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
			serviceProgress.setKeyProgressIncrement((int) (len * sSeekStepSize));
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
		showOverlays();
		reload();
	}

	@Override
	public void onPause() {
		mHandler.removeCallbacks(mAutoHideRunnable);
		mHandler.removeCallbacks(mIssueReloadRunnable);
		super.onPause();
	}


	public void autohide() {
		mHandler.removeCallbacks(mAutoHideRunnable);
		mHandler.postDelayed(mAutoHideRunnable, AUTOHIDE_DEFAULT_TIMEOUT);
	}

	public void showOverlays() {
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
		if (mServicesView != null)
			mServicesView.getLayoutManager().scrollToPosition(getCurrentServiceIndex());
		fadeInView(mServicesView);
		autohide();
	}

	private void hideZapOverlays() {
		View view = getView();
		if (view == null)
			return;
		fadeOutView(mServicesView);
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
			showOverlays();
	}

	protected boolean isOverlaysVisible() {
		View sdroot = getView().findViewById(R.id.overlay_root);
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
		mTitle = mServiceInfo.getString(Event.KEY_SERVICE_NAME);
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