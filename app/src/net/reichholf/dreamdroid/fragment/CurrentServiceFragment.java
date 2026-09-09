/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.loader.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.asynctask.GetCurrentServiceTask;
import net.reichholf.dreamdroid.enigma.CurrentService;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.CurrentServiceRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.ui.current.CurrentServiceScreenKt;
import net.reichholf.dreamdroid.ui.current.CurrentServiceUiState;
import net.reichholf.dreamdroid.ui.epg.EpgListMapper;

/**
 * Shows some information about the service currently running on TV.
 * Compose Material 3 UI; typed {@link CurrentService}; detail/timer still take
 * ExtendedHashMap at the edge.
 * 
 * @author sreichholf
 * 
 */
public class CurrentServiceFragment extends BaseHttpFragment
		implements GetCurrentServiceTask.GetCurrentServiceTaskHandler {
	@SuppressWarnings("unused")
	private static final String LOG_TAG = "CurrentServiceFragment";

	protected ProgressDialog mProgress;

	@Nullable
	private Service mService;
	@Nullable
	private Event mNow;
	@Nullable
	private Event mNext;
	private boolean mCurrentServiceReady;

	@Nullable
	@State public CurrentService mCurrent;
	@Nullable
	@State public ExtendedHashMap mCurrentItem;

	@Nullable
	private GetCurrentServiceTask mCurrentServiceTask;

	private CurrentServiceUiState mUiState;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.current_service));

		mCurrentServiceReady = false;
		mUiState = new CurrentServiceUiState();
	}

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.current_service, container, false);
		ComposeView compose = view.findViewById(R.id.compose_current);
		CurrentServiceScreenKt.bindCurrentServiceScreen(
				compose,
				mUiState,
				() -> {
					onItemSelected(Statics.ITEM_NOW);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_NEXT);
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					onItemSelected(Statics.ITEM_STREAM);
					return kotlin.Unit.INSTANCE;
				}
		);
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		if (mCurrent == null || mCurrent.isEmpty())
			mReload = true;

		super.onViewCreated(view, savedInstanceState);
		if (!mReload)
			applyCurrent(mCurrent);
	}

	@Override
	public void onDestroy() {
		if (mCurrentServiceTask != null) {
			mCurrentServiceTask.cancel(true);
		}
		super.onDestroy();
	}

	/**
	 * @param id
	 */
	@Override
	protected boolean onItemSelected(int id) {
		if (!mCurrentServiceReady) {
			showToast(getText(R.string.not_available));
			return true;
		}

		switch (id) {
		case Statics.ITEM_NOW:
			showEpgDetail(mNow);
			return true;
		case Statics.ITEM_NEXT:
			showEpgDetail(mNext);
			return true;
		case Statics.ITEM_STREAM:
			if (mService != null) {
				String ref = mService.getReference();
				String name = mService.getName();
				if (ref != null && !"".equals(ref)) {
					streamService(ref, name);
					return true;
				}
			}
			showToast(getText(R.string.not_available));
			return true;
		default:
			return super.onItemSelected(id);
		}
	}

	private void showEpgDetail(@Nullable Event event) {
		if (event != null) {
			mCurrentItem = EpgListMapper.toExtendedHashMap(event);
			Bundle args = new Bundle();
			args.putSerializable("currentItem", mCurrentItem);
			((MultiPaneHandler) getAppCompatActivity()).showDialogFragment(EpgDetailBottomSheet.class, args,
					"current_epg_detail_dialog");
		}
	}

	/**
	 * Called after loading the current service has finished to update the
	 * GUI-Content
	 */
	private void applyCurrent(@Nullable CurrentService content) {
		if (content != null && !content.isEmpty()) {
			mCurrent = content;
			mCurrentServiceReady = true;

			mService = content.getService();
			mNow = content.getNow();
			mNext = content.getNext();
			mUiState.apply(content);
		} else {
			mCurrentServiceReady = false;
			mUiState.clear();
			showToast(getText(R.string.not_available));
		}
	}

	/**
	 * @param event
	 */
	protected void setTimerByEventData(@NonNull ExtendedHashMap event) {
		Timer.editUsingEvent((MultiPaneHandler) getAppCompatActivity(), event, this);
	}

	/**
	 * @param event
	 */
	protected void setTimerById(@NonNull ExtendedHashMap event) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onSimpleResult
	 * (boolean, net.reichholf.dreamdroid.helpers.ExtendedHashMap)
	 */
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		super.onSimpleResult(success, result);
	}

	/**
	 * @param ref
	 *            A ServiceReference
	 */
	private void streamService(String ref, String name) {
		Intent intent = IntentFactory.getStreamServiceIntent(getAppCompatActivity(), ref, name);
		startActivity(intent);
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		switch (action) {
		case Statics.ACTION_SET_TIMER:
			setTimerById(mCurrentItem);
			break;
		case Statics.ACTION_EDIT_TIMER:
			setTimerByEventData(mCurrentItem);
			break;
		case Statics.ACTION_FIND_SIMILAR:
			findSimilarEvents(mCurrentItem);
			break;
		case Statics.ACTION_IMDB:
			IntentFactory.queryIMDb(getAppCompatActivity(), mCurrentItem);
			break;
		}
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		// Current service no longer starts this loader. BaseHttpFragment still requires LoaderCallbacks.
		return new AsyncSimpleLoader(getAppCompatActivity(), new CurrentServiceRequestHandler(),
				args);
	}

	@Override
	public void onLoadFinished(@NonNull Loader<LoaderResult<ExtendedHashMap>> loader,
			@NonNull LoaderResult<ExtendedHashMap> result) {
		// Unused: content comes from GetCurrentServiceTask / EnigmaClient.
	}

	@Override
	protected void reload() {
		mReload = false;
		loadCurrentService();
	}

	private void loadCurrentService() {
		mHttpHelper.onLoadStarted();
		if (!"".equals(getBaseTitle().trim())) {
			setCurrentTitle(getString(R.string.loading));
		}
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (mCurrentServiceTask != null) {
			mCurrentServiceTask.cancel(true);
		}
		mCurrentServiceTask = new GetCurrentServiceTask(this);
		mCurrentServiceTask.execute();
	}

	@Override
	public void onCurrentServiceReady(boolean success, @Nullable CurrentService current,
			@Nullable String errorText) {
		mHttpHelper.onLoadFinished();
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (!success) {
			mCurrentServiceReady = false;
			mUiState.clear();
			showToast(errorText != null ? errorText : getText(R.string.not_available));
			return;
		}
		applyCurrent(current);
	}
}
