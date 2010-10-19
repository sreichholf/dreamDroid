/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.CurrentService;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * Shows some information about the service currently running on TV
 * 
 * @author sreichholf
 * 
 */
public class CurrentServiceActivity extends AbstractHttpActivity {
	public static final int MENU_RELOAD = 0;

	private ExtendedHashMap mCurrent;
	private GetCurrentServiceTask mCurrentServiceTask;

	private TextView mServiceName;
	private TextView mProvider;
	private TextView mNowStart;
	private TextView mNowTitle;
	private TextView mNowDuration;
	private TextView mNextStart;
	private TextView mNextTitle;
	private TextView mNextDuration;

	/**
	 * <code>AsyncTask</code> to fetch the current service information async.
	 * 
	 * @author sre
	 * 
	 */
	private class GetCurrentServiceTask extends AsyncTask<Void, String, Boolean> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(Void... unused) {
			publishProgress(getText(R.string.app_name) + "::" + getText(R.string.current_service) + " - "
					+ getText(R.string.fetching_data));

			mCurrent.clear();

			String xml = CurrentService.get(mShc);
			if (xml != null) {
				publishProgress(getText(R.string.app_name) + "::" + getText(R.string.current_service) + " - "
						+ getText(R.string.parsing));

				if (CurrentService.parse(xml, mCurrent)) {
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
				title = getText(R.string.app_name) + "::" + getText(R.string.current_service);

				onCurrentServiceReady();
			} else {
				title = getText(R.string.app_name) + "::" + getText(R.string.current_service) + " - "
						+ getText(R.string.get_content_error);

				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			setTitle(title);

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.AbstractHttpActivity#onCreate(android
	 * .os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.current_service);

		mServiceName = (TextView) findViewById(R.id.service_name);
		mProvider = (TextView) findViewById(R.id.provider);
		mNowStart = (TextView) findViewById(R.id.event_now_start);
		mNowTitle = (TextView) findViewById(R.id.event_now_title);
		mNowDuration = (TextView) findViewById(R.id.event_now_duration);
		mNextStart = (TextView) findViewById(R.id.event_next_start);
		mNextTitle = (TextView) findViewById(R.id.event_next_title);
		mNextDuration = (TextView) findViewById(R.id.event_next_duration);

		mCurrent = new ExtendedHashMap();

		reload();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);

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
		default:
			return false;
		}
	}

	/**
	 * Reloads all current service information
	 */
	private void reload() {
		if (mCurrentServiceTask != null) {
			mCurrentServiceTask.cancel(true);
		}

		mCurrentServiceTask = new GetCurrentServiceTask();
		mCurrentServiceTask.execute();
	}

	/**
	 * Called after loading the current service has finished to update the
	 * GUI-Content
	 */
	@SuppressWarnings("unchecked")
	private void onCurrentServiceReady() {
		ExtendedHashMap service = (ExtendedHashMap) mCurrent.get(CurrentService.SERVICE);
		ArrayList<ExtendedHashMap> events = (ArrayList<ExtendedHashMap>) mCurrent.get(CurrentService.EVENTS);
		ExtendedHashMap now = events.get(0);
		ExtendedHashMap next = events.get(1);

		mServiceName.setText(service.getString(CurrentService.SERVICE_NAME));
		mProvider.setText(service.getString(CurrentService.SERVICE_PROVIDER));
		// Now
		mNowStart.setText(now.getString(Event.EVENT_START_READABLE));
		mNowTitle.setText(now.getString(Event.EVENT_TITLE));
		mNowDuration.setText(now.getString(Event.EVENT_DURATION_READABLE));
		// Next
		mNextStart.setText(next.getString(Event.EVENT_START_READABLE));
		mNextTitle.setText(next.getString(Event.EVENT_TITLE));
		mNextDuration.setText(next.getString(Event.EVENT_DURATION_READABLE));
	}
}
