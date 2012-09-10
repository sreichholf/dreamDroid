/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.adapter.TimerListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerCleanupRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;

import org.apache.http.NameValuePair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ListView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Activity to show a List of all existing timers of the target device
 * 
 * @author sreichholf
 * 
 */
public class TimerListFragment extends AbstractHttpListFragment {
	private ExtendedHashMap mTimer;
	private ProgressDialog mProgress;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);

		mCurrentTitle = mBaseTitle = getString(R.string.timer);

		setHasOptionsMenu(true);
		setAdapter();

		if (savedInstanceState != null) {
			mTimer = (ExtendedHashMap) savedInstanceState.getParcelable("timer");
		} else {
			reload();
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("timer", mTimer);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mTimer = mMapList.get((int) id);
		getSherlockActivity().showDialog(Statics.DIALOG_TIMER_SELECTED_ID);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_EDIT_TIMER) {
			if (resultCode == Activity.RESULT_OK) {
				reload();
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.reload, menu);
		inflater.inflate(R.menu.timerlist, menu);
	}

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
	 * Open a <code>TimerEditActivity</code> for timer editing
	 * 
	 * @param timer
	 *            The timer to be edited
	 */
	private void editTimer(ExtendedHashMap timer, boolean create) {
		Timer.edit(mMultiPaneHandler, timer, this, create);
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new TimerListAdapter(getSherlockActivity(), R.layout.timer_list_item, mMapList);
		setListAdapter(mAdapter);
	}

	/**
	 * Confirmation dialog before timer deletion
	 */
	private void deleteTimerConfirm() {
		getSherlockActivity().showDialog(Statics.DIALOG_TIMER_DELETE_CONFIRM_ID);
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
		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.cleaning_timerlist), true);
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

		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.cleaning_timerlist), true);
		execSimpleResultTask(new TimerCleanupRequestHandler(), new ArrayList<NameValuePair>());
	}

	@Override
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
			reload();
		}
	}

	@Override
	public Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case (Statics.DIALOG_TIMER_SELECTED_ID):
			CharSequence[] actions = { getText(R.string.edit), getText(R.string.delete) };
			AlertDialog.Builder adBuilder = new AlertDialog.Builder(getSherlockActivity());
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
			dialog = adBuilder.create();
			break;
		case (Statics.DIALOG_TIMER_DELETE_CONFIRM_ID):
			AlertDialog.Builder builder = new AlertDialog.Builder(getSherlockActivity());
			builder.setTitle(mTimer.getString(Timer.KEY_NAME)).setMessage(getText(R.string.delete_confirm))
					.setCancelable(false)
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							deleteTimer(mTimer);
							dialog.dismiss();
						}
					}).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
						}
					});
			dialog = builder.create();
			break;
		}
		return dialog;
	}

	@Override
	public Loader<ArrayList<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getSherlockActivity(), new TimerListRequestHandler(), false, args);
		return loader;
	}
}
