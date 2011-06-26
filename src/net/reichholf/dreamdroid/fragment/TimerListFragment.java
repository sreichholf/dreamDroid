/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.adapter.TimerListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerCleanupRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerListRequestHandler;

import org.apache.http.NameValuePair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

/**
 * Activity to show a List of all existing timers of the target device
 * 
 * @author sreichholf
 * 
 */
public class TimerListFragment extends AbstractHttpListFragment {
	private ExtendedHashMap mTimer;
	private ProgressDialog mProgress;
	private GetTimerListTask mListTask;
	private MultiPaneHandler mMultiPaneHandler;

	/**
	 * Get the list of all timers async.
	 * 
	 * @author sre
	 * 
	 */
	private class GetTimerListTask extends AsyncListUpdateTask {
		public GetTimerListTask(){
			super(getString(R.string.timer), new TimerListRequestHandler(), false);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.activities.AbstractHttpListActivity#onCreate
	 * (android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mMultiPaneHandler = (MultiPaneHandler) getActivity();
		
		setHasOptionsMenu(true);
		setAdapter();
		
		if(savedInstanceState == null){
			reload();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		if (mListTask != null) {
			mListTask.cancel(true);
		}

		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mTimer = mMapList.get((int) id);

		CharSequence[] actions = { getText(R.string.edit), getText(R.string.delete) };

		AlertDialog.Builder adBuilder = new AlertDialog.Builder(getActivity());
		adBuilder.setTitle(R.string.pick_action);
		adBuilder.setItems(actions, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case 0:
					editTimer(mTimer, false);
					break;
				case 1:
					deleteTimerConfirm();
					break;
				}
			}
		});

		AlertDialog alert = adBuilder.create();
		alert.show();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_CHANGE_TIMER) {
			if (resultCode == Activity.RESULT_OK) {
				reload();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		menu.add(0, Statics.ITEM_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, Statics.ITEM_NEW_TIMER, 0, getText(R.string.new_timer)).setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, Statics.ITEM_CLEANUP, 0, getText(R.string.cleanup)).setIcon(android.R.drawable.ic_menu_manage);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case (Statics.ITEM_RELOAD):
			reload();
			return true;
		case (Statics.ITEM_NEW_TIMER):
			mTimer = Timer.getInitialTimer();
			editTimer(mTimer, true);
			return true;
		case (Statics.ITEM_CLEANUP):
			cleanupTimerList();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Reload the list of timers by calling an <code>GetTimerListTask</code>
	 */
	@SuppressWarnings("unchecked")
	public void reload() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		if (mListTask != null) {
			mListTask.cancel(true);
		}

		mListTask = new GetTimerListTask();
		mListTask.execute(params);
	}

	/**
	 * Open a <code>TimerEditActivity</code> for timer editing
	 * 
	 * @param timer
	 *            The timer to be edited
	 */
	private void editTimer(ExtendedHashMap timer, boolean newTimer) {
//		Intent intent = new Intent(getActivity(), TimerEditActivity.class);
		TimerEditFragment f = new TimerEditFragment();
		Bundle args = new Bundle();
		
		ExtendedHashMap data = new ExtendedHashMap();
		data.put("timer", timer);

		args.putSerializable(sData, data);

		if (!newTimer) {
			args.putString("action", Intent.ACTION_EDIT);
		} else {
			args.putString("action", DreamDroid.ACTION_NEW);
		}
		
		f.setArguments(args);
		mMultiPaneHandler.showDetails(f);
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {		
		mAdapter = new TimerListAdapter(getActivity(), R.layout.timer_list_item, mMapList);
		setListAdapter(mAdapter);
	}

	/**
	 * Confirmation dialog before timer deletion
	 */
	private void deleteTimerConfirm() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		builder.setTitle(mTimer.getString(Timer.KEY_NAME)).setMessage(getText(R.string.delete_confirm))
				.setCancelable(false).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						deleteTimer(mTimer);
						dialog.dismiss();
					}
				}).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Delete a timer by creating an <code>DeleteTimerTask</code>
	 * 
	 * @param timer
	 *            The Timer to delete as <code>ExtendedHashMap</code>
	 */
	private void deleteTimer(ExtendedHashMap timer) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		ArrayList<NameValuePair> params = Timer.getDeleteParams(timer);
		mProgress = ProgressDialog.show(getActivity(), "", getText(R.string.cleaning_timerlist), true);
		execSimpleResultTask(new TimerDeleteRequestHandler(), params);
	}

	/**
	 * CleanUp timer list by creating an <code>CleanupTimerListTask</code>
	 */
	private void cleanupTimerList() {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		
		mProgress = ProgressDialog.show(getActivity(), "", getText(R.string.cleaning_timerlist), true);
		execSimpleResultTask(new TimerCleanupRequestHandler(), new ArrayList<NameValuePair>());
	}
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onSimpleResult(boolean, net.reichholf.dreamdroid.helpers.ExtendedHashMap)
	 */
	@Override
	protected void onSimpleResult(boolean success, ExtendedHashMap result){		
		if(mProgress != null){		
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);
		
		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
			reload();
		}
	}

	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.fragment.ActivityCallbackHandler#onCreateDialog(int)
	 */
	@Override
	public Dialog onCreateDialog(int id) {
		// TODO Auto-generated method stub
		return null;
	}
}
