/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.net.URLEncoder;
import java.util.ArrayList;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;

/**
 * @author sre
 * 
 */
public class DreamDroidShareActivity extends SherlockListActivity {
	public static String LOG_TAG = DreamDroidShareActivity.class.getSimpleName();

	private SimpleResultTask mSimpleResultTask;
	private SimpleHttpClient mShc;
	private SimpleAdapter mAdapter;
	private ArrayList<ExtendedHashMap> mProfileMapList;
	private ProgressDialog mProgress;

	ArrayList<Profile> mProfiles;

	protected class SimpleResultTask extends AsyncTask<ArrayList<NameValuePair>, Void, Boolean> {
		private ExtendedHashMap mResult;
		private SimpleResultRequestHandler mHandler;

		public SimpleResultTask(SimpleResultRequestHandler handler) {
			mHandler = handler;
		}

		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			if (isCancelled())
				return false;
			publishProgress();
			String xml = mHandler.get(mShc, params[0]);

			if (xml != null) {
				ExtendedHashMap result = mHandler.parseSimpleResult(xml);

				String stateText = result.getString("statetext");

				if (stateText != null) {
					mResult = result;
					return true;
				}
			}

			return false;
		}

		@Override
		protected void onProgressUpdate(Void... progress) {
			if (!isCancelled())
				DreamDroidShareActivity.this.setProgressBarIndeterminateVisibility(true);
		}

		protected void onPostExecute(Boolean result) {
			DreamDroidShareActivity.this.setProgressBarIndeterminateVisibility(false);

			if (!result || mResult == null) {
				mResult = new ExtendedHashMap();
			}

			DreamDroidShareActivity.this.onSimpleResult(result, mResult);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getText(R.string.watch_on_dream));
		load();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		Profile profile = mProfiles.get(position);
		playOnDream(profile);
	}

	private void playOnDream(Profile p) {
		String url = null;
		Intent i = getIntent();
		mShc = SimpleHttpClient.getInstance(p);
		if (Intent.ACTION_SEND.equals(i.getAction()))
			url = i.getExtras().getString(Intent.EXTRA_TEXT);
		else if (Intent.ACTION_VIEW.equals(i.getAction()))
			url = i.getDataString();

		if (url != null) {
			Log.i(LOG_TAG, url);
			Log.i(LOG_TAG, p.getHost());

			url = URLEncoder.encode(url).replace("+", "%20");
			String ref = "4097:0:1:0:0:0:0:0:0:0:" + url;
			ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("file", ref));
			execSimpleResultTask(params);
		} else {
			finish();
		}
	}

	public void load() {
		DatabaseHelper dbh = DatabaseHelper.getInstance(this);
		mProfileMapList = new ArrayList<ExtendedHashMap>();
		mProfileMapList.clear();
		mProfiles = dbh.getProfiles();
		for (Profile m : mProfiles) {
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(DatabaseHelper.KEY_PROFILE, m.getName());
			map.put(DatabaseHelper.KEY_HOST, m.getHost());
			mProfileMapList.add(map);
		}

		mAdapter = new SimpleAdapter(this, mProfileMapList, android.R.layout.two_line_list_item, new String[] {
				DatabaseHelper.KEY_PROFILE, DatabaseHelper.KEY_HOST }, new int[] { android.R.id.text1,
				android.R.id.text2 });
		setListAdapter(mAdapter);
		mAdapter.notifyDataSetChanged();
	}

	@SuppressWarnings("unchecked")
	public void execSimpleResultTask(ArrayList<NameValuePair> params) {
		if (mSimpleResultTask != null) {
			mSimpleResultTask.cancel(true);
		}
		mProgress = ProgressDialog.show(this, getString(R.string.loading), getString(R.string.loading));
		SimpleResultRequestHandler handler = new SimpleResultRequestHandler(URIStore.MEDIA_PLAYER_PLAY);
		mSimpleResultTask = new SimpleResultTask(handler);
		mSimpleResultTask.execute(params);
	}

	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null)
			mProgress.dismiss();

		String toastText = (String) getText(R.string.sent);
		if (mShc.hasError()) {
			toastText = mShc.getErrorText();
		}

		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
		finish();
	}
}
