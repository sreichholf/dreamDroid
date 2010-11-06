/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.ListHandler.TimerListRequestHandler;

import org.apache.http.NameValuePair;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * Activity to show a List of all existing timers of the target device
 * 
 * @author sreichholf
 * 
 */
public class TimerListActivity extends AbstractHttpListActivity {
	public static final int CHANGE_TIMER_REQUEST = 0;

	public static final int MENU_RELOAD = 0;
	public static final int MENU_NEW_TIMER = 1;
	public static final int MENU_CLEANUP = 2;

	private ExtendedHashMap mTimer;
	private ProgressDialog mProgress;
	private GetTimerListTask mListTask;
	private DeleteTimerTask mDeleteTask;
	private CleanupTimerListTask mCleanupTask;

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

	/**
	 * Delete a specific timer async
	 * 
	 * @author sre
	 * 
	 */
	private class DeleteTimerTask extends AsyncTask<Void, Void, Boolean> {
		private ExtendedHashMap mResult;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(Void... params) {
			String xml = Timer.delete(mShc, mTimer);

			if (xml != null) {
				ExtendedHashMap result = Timer.parseSimpleResult(xml);

				String stateText = result.getString(SimpleResult.STATE_TEXT);

				if (stateText != null) {
					mResult = result;
					return true;
				}
			}

			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			if (!result) {
				mResult = new ExtendedHashMap();
			}
			onTimerDeleted(mResult);
		}
	}

	private class CleanupTimerListTask extends AsyncTask<Void, Void, Boolean> {
		private ExtendedHashMap mResult;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(Void... params) {
			String xml = Timer.cleanupTimers(mShc);

			if (xml != null) {
				ExtendedHashMap result = Timer.parseSimpleResult(xml);

				String stateText = result.getString(SimpleResult.STATE_TEXT);

				if (stateText != null) {
					mResult = result;
					return true;
				}
			}

			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			if (!result) {
				mResult = new ExtendedHashMap();
			}
			onTimerCleanupFinished(mResult);
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

		setAdapter();
		reload();
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
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mTimer = mMapList.get((int) id);

		CharSequence[] actions = { getText(R.string.edit), getText(R.string.delete) };

		AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
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
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CHANGE_TIMER_REQUEST) {
			if (resultCode == RESULT_OK) {
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
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, MENU_NEW_TIMER, 0, getText(R.string.new_timer)).setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, MENU_CLEANUP, 0, getText(R.string.cleanup)).setIcon(android.R.drawable.ic_menu_manage);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case (MENU_RELOAD):
			reload();
			return true;
		case (MENU_NEW_TIMER):
			mTimer = Timer.getNewTimer();
			editTimer(mTimer, true);
			return true;
		case (MENU_CLEANUP):
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
		Intent intent = new Intent(this, TimerEditActivity.class);

		ExtendedHashMap data = new ExtendedHashMap();
		data.put("timer", timer);

		intent.putExtra(sData, data);

		if (!newTimer) {
			intent.setAction(Intent.ACTION_EDIT);
		} else {
			intent.setAction(DreamDroid.ACTION_NEW);
		}

		this.startActivityForResult(intent, CHANGE_TIMER_REQUEST);
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new SimpleAdapter(this, mMapList, R.layout.timer_list_item, new String[] { Timer.NAME,
				Timer.SERVICE_NAME, Timer.BEGIN_READEABLE, Timer.END_READABLE }, new int[] { R.id.timer_name,
				R.id.service_name, R.id.timer_start, R.id.timer_end });
		setListAdapter(mAdapter);
	}

	/**
	 * Confirmation dialog before timer deletion
	 */
	private void deleteTimerConfirm() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(mTimer.getString(Timer.NAME)).setMessage(getText(R.string.delete_confirm))
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
		if (mDeleteTask != null) {
			mDeleteTask.cancel(true);
			if (mProgress != null) {
				if (mProgress.isShowing()) {
					mProgress.dismiss();
				}
			}
		}

		mProgress = ProgressDialog.show(this, "", getText(R.string.deleting), true);

		mDeleteTask = new DeleteTimerTask();
		mDeleteTask.execute();
	}

	/**
	 * Called after a timer has been deleted
	 * 
	 * @param result
	 *            A SimpleXmlResult-like <code>ExtendedHashMap</code>
	 */
	private void onTimerDeleted(ExtendedHashMap result) {
		mProgress.dismiss();

		String toastText = (String) getText(R.string.get_content_error);
		String stateText = result.getString(SimpleResult.STATE_TEXT);

		if (stateText != null && !"".equals(stateText)) {
			toastText = stateText;
		}

		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();

		if (Python.TRUE.equals(result.getString(SimpleResult.STATE))) {
			reload();
		}
	}

	/**
	 * CleanUp timer list by creating an <code>CleanupTimerListTask</code>
	 */
	private void cleanupTimerList() {
		if (mCleanupTask != null) {
			mCleanupTask.cancel(true);
			if (mProgress != null) {
				if (mProgress.isShowing()) {
					mProgress.dismiss();
				}
			}
		}

		mProgress = ProgressDialog.show(this, "", getText(R.string.cleaning_timerlist), true);

		mCleanupTask = new CleanupTimerListTask();
		mCleanupTask.execute();
	}

	/**
	 * Called after timerlist has been cleaned up
	 * 
	 * @param result
	 *            A SimpleXmlResult-like <code>ExtendedHashMap</code>
	 */
	private void onTimerCleanupFinished(ExtendedHashMap result) {
		mProgress.dismiss();

		String toastText = (String) getText(R.string.get_content_error);
		String stateText = result.getString(SimpleResult.STATE_TEXT);

		if (stateText != null && !"".equals(stateText)) {
			toastText = stateText;
		}

		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();

		if (Python.TRUE.equals(result.getString(SimpleResult.STATE))) {
			reload();
		}
	}
}
