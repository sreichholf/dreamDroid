/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.EpgSearchFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailDialog;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

/**
 * @author sreichholf
 * 
 */
public abstract class AbstractHttpEventListFragment extends AbstractHttpListFragment implements
		ActionDialog.DialogActionListener {

	protected String mReference;
	protected String mName;

	protected ProgressDialog mProgress;
	protected ExtendedHashMap mCurrentItem;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			mReference = savedInstanceState.getString("reference");
			mName = savedInstanceState.getString("name");
			mCurrentItem = (ExtendedHashMap) savedInstanceState.getParcelable("currentItem");
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("reference", mReference);
		outState.putString("name", mName);
		outState.putParcelable("currentItem", mCurrentItem);

		super.onSaveInstanceState(outState);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mCurrentItem = mMapList.get((int) id);
		Bundle args = new Bundle();
		args.putParcelable("currentItem", mCurrentItem);
		getMultiPaneHandler().showDialogFragment(EpgDetailDialog.class, args, "epg_detail_dialog");
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

		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
	}

	/**
	 * @param event
	 */
	protected void findSimilarEvents(ExtendedHashMap event) {
		// TODO fix findSimilarEvents
		EpgSearchFragment f = new EpgSearchFragment();
		Bundle args = new Bundle();
		args.putString(SearchManager.QUERY, event.getString(Event.KEY_EVENT_TITLE));

		f.setArguments(args);
		getMultiPaneHandler().showDetails(f);
	}

	@Override
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		super.onSimpleResult(success, result);
	}

	/**
	 * @param event
	 */
	protected void setTimerByEventData(ExtendedHashMap event) {
		Timer.editUsingEvent(getMultiPaneHandler(), event, this);
	}

	public void onDialogAction(int action, Object details) {
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
			IntentFactory.queryIMDb(getSherlockActivity(), mCurrentItem);
			break;
		}
	}
}
