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
import net.reichholf.dreamdroid.helpers.enigma2.Location;
import net.reichholf.dreamdroid.helpers.enigma2.Python;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;

import org.apache.http.NameValuePair;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * @author sreichholf
 * 
 */
public class TimerListActivity extends AbstractHttpListActivity {
	public static final int CHANGE_TIMER_REQUEST = 0;

	public static final int MENU_RELOAD = 0;
	public static final int MENU_NEW_TIMER = 1;

	private ExtendedHashMap mTimer;
	private ArrayList<String> mLocations;
	private ProgressDialog mDeleteProgress;
	private AsyncTask<ArrayList<NameValuePair>, String, Boolean> mListTask;
	private AsyncTask<String, String, Boolean> mDeleteTask;

	private class GetTimerListTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			publishProgress(getText(R.string.app_name) + "::" + getText(R.string.timer) + " - "
					+ getText(R.string.fetching_data));

			mList.clear();
			String xml = Timer.getList(mShc, params);

			if (xml != null) {
				publishProgress(getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.timer)
						+ " - " + getText(R.string.parsing));

				if (Timer.parseList(xml, mList)) {
					publishProgress(getText(R.string.app_name) + "::" + getText(R.string.timer) + " - "
							+ getText(R.string.locations) + " - " + getText(R.string.fetching_data));

					mLocations.clear();

					boolean gotLoc = true;
					String xmlLoc = Location.getList(mShc);

					if (xmlLoc != null) {
						publishProgress(getText(R.string.app_name) + "::" + getText(R.string.timer) + " - "
								+ getText(R.string.locations) + " - " + getText(R.string.parsing));

						if (!Location.parseList(xmlLoc, mLocations)) {
							Log.e(DreamDroid.LOG_TAG, "Error parsing locations, falling back to /hdd/movie");
							gotLoc = false;
						}
					}

					// fallback to default if an error occured while
					// fetching/parsing locations
					if (!gotLoc) {
						mLocations = new ArrayList<String>();
						mLocations.add("/hdd/movie");
					}

					return true;
				}
			}

			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			setTitle(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			String title = null;

			if (result) {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.timer);
				
				mAdapter.notifyDataSetChanged();				
				if(mList.size() == 0){
					showDialog(DIALOG_EMPTY_LIST_ID);
				}
			} else {
				title = getText(net.reichholf.dreamdroid.R.string.app_name) + "::" + getText(R.string.timer) + " - "
						+ getText(R.string.get_content_error);

				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			setTitle(title);

		}
	}

	private class DeleteTimerTask extends AsyncTask<String, String, Boolean> {
		private ExtendedHashMap mResult;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(String... params) {
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
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {

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

		if (savedInstanceState == null) {
			mLocations = new ArrayList<String>();
			setAdapter();
			reload();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seenet.reichholf.dreamdroid.activities.AbstractHttpListActivity#
	 * onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		setAdapter();
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
		mTimer = mList.get((int) id);

		CharSequence[] actions = { getText(R.string.edit), getText(R.string.delete) };

		AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
		adBuilder.setTitle("Pick an action");
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
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, MENU_NEW_TIMER, 0, getText(R.string.new_timer)).setIcon(android.R.drawable.ic_menu_add);
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
		}
		return false;
	}

	/**
	 * 
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
	 * @param timer
	 */
	private void editTimer(ExtendedHashMap timer, boolean newTimer) {
		Intent intent = new Intent(this, TimerEditActivity.class);

		ExtendedHashMap data = new ExtendedHashMap();
		data.put("timer", timer);
		data.put("locations", mLocations);

		intent.putExtra(sData, data);

		if (!newTimer) {
			intent.setAction(Intent.ACTION_EDIT);
		} else {
			intent.setAction(DreamDroid.ACTION_NEW);
		}

		this.startActivityForResult(intent, CHANGE_TIMER_REQUEST);
	}

	private void setAdapter() {
		mAdapter = new SimpleAdapter(this, mList, R.layout.timer_list_item, new String[] { Timer.NAME,
				Timer.SERVICE_NAME, Timer.BEGIN_READEABLE, Timer.END_READABLE }, new int[] { R.id.timer_name,
				R.id.service_name, R.id.timer_start, R.id.timer_end });
		setListAdapter(mAdapter);
	}

	/**
	 * @param timer
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
	 * @param timer
	 */
	private void deleteTimer(ExtendedHashMap timer) {
		if (mDeleteTask != null) {
			mDeleteTask.cancel(true);
			if (mDeleteProgress != null) {
				if (mDeleteProgress.isShowing()) {
					mDeleteProgress.dismiss();
				}
			}
		}

		mDeleteProgress = ProgressDialog.show(this, "", getText(R.string.deleting), true);

		mDeleteTask = new DeleteTimerTask();
		mDeleteTask.execute("");
	}

	/**
	 * @param result
	 */
	private void onTimerDeleted(ExtendedHashMap result) {
		mDeleteProgress.dismiss();

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
