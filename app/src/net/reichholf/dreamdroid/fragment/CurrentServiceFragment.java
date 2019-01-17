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
import androidx.loader.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.CurrentService;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.CurrentServiceRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;

/**
 * Shows some information about the service currently running on TV
 * 
 * @author sreichholf
 * 
 */
public class CurrentServiceFragment extends BaseHttpFragment {
	@SuppressWarnings("unused")
	private static final String LOG_TAG = "CurrentServiceFragment";

	private TextView mServiceName;
	private TextView mProvider;
	private TextView mNowStart;
	private TextView mNowTitle;
	private TextView mNowDesc;
	private TextView mNowDuration;
	private TextView mNextStart;
	private TextView mNextTitle;
	private TextView mNextDesc;
	private TextView mNextDuration;
	private Button mStream;
	private LinearLayout mNowLayout;
	private LinearLayout mNextLayout;
	protected ProgressDialog mProgress;

	private ExtendedHashMap mService;
	private ExtendedHashMap mNow;
	private ExtendedHashMap mNext;
	private boolean mCurrentServiceReady;

	@State public ExtendedHashMap mCurrent;
	@State public ExtendedHashMap mCurrentItem;

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.current_service));

		mCurrentServiceReady = false;
	}

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.current_service, container, false);

		mServiceName = view.findViewById(R.id.service_name);
		mProvider = view.findViewById(R.id.provider);
		mNowStart = view.findViewById(R.id.event_now_start);
		mNowTitle = view.findViewById(R.id.event_now_title);
		mNowDesc = view.findViewById(R.id.event_now_desc);
		mNowDuration = view.findViewById(R.id.event_now_duration);
		mNextStart = view.findViewById(R.id.event_next_start);
		mNextTitle = view.findViewById(R.id.event_next_title);
		mNextDesc = view.findViewById(R.id.event_next_desc);
		mNextDuration = view.findViewById(R.id.event_next_duration);
		mStream = view.findViewById(R.id.ButtonStream);
		mNowLayout = view.findViewById(R.id.layout_now);
		mNextLayout = view.findViewById(R.id.layout_next);

		registerOnClickListener(mNowLayout, Statics.ITEM_NOW);
		registerOnClickListener(mNextLayout, Statics.ITEM_NEXT);
		registerOnClickListener(mStream, Statics.ITEM_STREAM);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		if (mCurrent == null || mCurrent.isEmpty())
			mReload = true;

		super.onViewCreated(view, savedInstanceState);
		if(!mReload)
			applyData(0, mCurrent);
	}

	/**
	 * Register an <code>OnClickListener</code> for a view and a specific item
	 * ID (<code>ITEM_*</code> statics)
	 * 
	 * @param v
	 *            The view an OnClickListener should be registered for
	 * @param id
	 *            The id used to identify the item clicked (<code>ITEM_*</code>
	 *            statics)
	 */
	protected void registerOnClickListener(View v, final int id) {
		v.setOnClickListener(v1 -> onItemSelected(id));
	}

	/**
	 * @param id
	 */
	@Override
	protected boolean onItemSelected(int id) {
		if(!mCurrentServiceReady){
			showToast(getText(R.string.not_available));
			return true;
		}

		String ref;
		switch (id) {
		case Statics.ITEM_NOW:
			showEpgDetail(mNow);
			return true;
		case Statics.ITEM_NEXT:
			showEpgDetail(mNext);
			return true;
		case Statics.ITEM_STREAM:
			ref = mService.getString(Service.KEY_REFERENCE);
			String name = mService.getString(Service.KEY_NAME);
			if (!"".equals(ref) && ref != null) {
				streamService(ref, name);
			} else {
				showToast(getText(R.string.not_available));
			}
			return true;
		default:
			return super.onItemSelected(id);
		}
	}

	private void showEpgDetail(ExtendedHashMap event) {
		if (event != null) {
			mCurrentItem = event;
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
	@Override
	public void applyData(int loaderId, ExtendedHashMap content) {
		if (content != null && !content.isEmpty()) {
			mCurrent = content;
			mCurrentServiceReady = true;

			mService = (ExtendedHashMap) mCurrent.get(CurrentService.KEY_SERVICE);
			@SuppressWarnings("unchecked")
			ArrayList<ExtendedHashMap> events = (ArrayList<ExtendedHashMap>) mCurrent.get(CurrentService.KEY_EVENTS);
			mNow = events.get(0);
			mNext = events.get(1);

			mServiceName.setText(mService.getString(CurrentService.KEY_SERVICE_NAME));
			mProvider.setText(mService.getString(CurrentService.KEY_SERVICE_PROVIDER));
			// Now
			mNowStart.setText(mNow.getString(Event.KEY_EVENT_START_READABLE));
			mNowTitle.setText(mNow.getString(Event.KEY_EVENT_TITLE));
			mNowDesc.setText(mNow.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED, ""));
			mNowDuration.setText(mNow.getString(Event.KEY_EVENT_DURATION_READABLE));
			// Next
			mNextStart.setText(mNext.getString(Event.KEY_EVENT_START_READABLE));
			mNextTitle.setText(mNext.getString(Event.KEY_EVENT_TITLE));
			mNextDesc.setText(mNext.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED, ""));
			mNextDuration.setText(mNext.getString(Event.KEY_EVENT_DURATION_READABLE));

			ImageView piconView = getView().findViewById(R.id.picon);
			Picon.setPiconForView(getAppCompatActivity(), piconView, mService, Statics.TAG_PICON);
		} else {
			showToast(getText(R.string.not_available));
		}
	}

	/**
	 * @param event
	 */
	protected void setTimerByEventData(ExtendedHashMap event) {
		Timer.editUsingEvent((MultiPaneHandler) getAppCompatActivity(), event, this);
	}

	/**
	 * @param event
	 */
	protected void setTimerById(ExtendedHashMap event) {
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
		return new AsyncSimpleLoader(getAppCompatActivity(), new CurrentServiceRequestHandler(),
				args);
	}
}
