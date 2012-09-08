/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import java.util.ArrayList;
import java.util.HashMap;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.activities.TabbedNavigationActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Volume;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.MenuItem;

/**
 * @author sreichholf
 * 
 */

public abstract class AbstractHttpListFragment extends DreamDroidListFragment implements
		LoaderManager.LoaderCallbacks<ArrayList<ExtendedHashMap>> {
	public static final String BUNDLE_KEY_LIST = "list";

	protected final String sData = "data";
	protected ArrayList<ExtendedHashMap> mMapList;
	protected ExtendedHashMap mData;
	protected Bundle mExtras;
	protected BaseAdapter mAdapter;
	protected SimpleHttpClient mShc;

	protected SimpleResultTask mSimpleResultTask;
	protected SetVolumeTask mVolumeTask;

	/**
	 * @author sre
	 * 
	 */
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
				getSherlockActivity().setProgressBarIndeterminateVisibility(true);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			getSherlockActivity().setProgressBarIndeterminateVisibility(false);

			if (!result || mResult == null) {
				mResult = new ExtendedHashMap();
			}

			onSimpleResult(result, mResult);
		}
	}

	protected class SetVolumeTask extends AsyncTask<ArrayList<NameValuePair>, Void, Boolean> {
		private ExtendedHashMap mVolume;
		private VolumeRequestHandler mHandler;

		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			if (isCancelled())
				return false;
			publishProgress();
			mHandler = new VolumeRequestHandler();
			String xml = mHandler.get(mShc, params[0]);

			if (xml != null) {
				ExtendedHashMap volume = new ExtendedHashMap();
				mHandler.parse(xml, volume);

				String current = volume.getString(Volume.KEY_CURRENT);
				if (current != null) {
					mVolume = volume;
					return true;
				}
			}

			return false;
		}

		@Override
		protected void onProgressUpdate(Void... progress) {
			if (!isCancelled())
				getSherlockActivity().setProgressBarIndeterminateVisibility(true);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			getSherlockActivity().setProgressBarIndeterminateVisibility(false);

			if (!result || mVolume == null) {
				mVolume = new ExtendedHashMap();
			}

			onVolumeSet(result, mVolume);
		}
	}

	protected void setDefaultLocation() {
		throw new UnsupportedOperationException("Required Method setDefaultLocation() not re-implemented");
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);

		mExtras = getArguments();
		mMapList = null;

		if (savedInstanceState != null) {
			mMapList = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_LIST);
		} else {
			mMapList = new ArrayList<ExtendedHashMap>();
		}

		if (mExtras != null) {
			HashMap<String, Object> map = (HashMap<String, Object>) mExtras.getSerializable("data");
			if (map != null) {
				mData = new ExtendedHashMap(map);
			}
		} else {
			mExtras = new Bundle();
		}

		setClient();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getListView().setFastScrollEnabled(true);
		try {
			setEmptyText(getText(R.string.loading));
		} catch (IllegalStateException e) {
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable(BUNDLE_KEY_LIST, mMapList);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemClicked(item.getItemId());
	}

	/**
	 * 
	 */
	private void setClient() {
		mShc = SimpleHttpClient.getInstance();
	}

	/**
	 * @param key
	 * @return
	 */
	public String getDataForKey(String key) {
		if (mData != null) {
			return (String) mData.get(key);
		}

		return null;
	}

	/**
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public String getDataForKey(String key, String defaultValue) {
		if (mData != null) {
			String str = (String) mData.get(key);
			if (str != null) {
				return str;
			}
		}
		return defaultValue;
	}

	/**
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public boolean getDataForKey(String key, boolean defaultValue) {
		if (mData != null) {
			Boolean b = (Boolean) mData.get(key);
			if (b != null) {
				return b.booleanValue();
			}
		}

		return defaultValue;
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
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onItemClicked(id);
			}
		});
	}

	/**
	 * @param id
	 */
	protected boolean onItemClicked(int id) {
		Intent intent;
		switch (id) {
		case Statics.ITEM_HOME:
			intent = new Intent(getSherlockActivity(), TabbedNavigationActivity.class);
			startActivity(intent);
			return true;
		default:
			return false;
		}
	}

	/**
	 * @param handler
	 * @param params
	 */
	@SuppressWarnings("unchecked")
	public void execSimpleResultTask(SimpleResultRequestHandler handler, ArrayList<NameValuePair> params) {
		if (mSimpleResultTask != null) {
			mSimpleResultTask.cancel(true);
		}

		mSimpleResultTask = new SimpleResultTask(handler);
		mSimpleResultTask.execute(params);
	}

	/**
	 * @param ref
	 *            The ServiceReference to zap to
	 */
	public void zapTo(String ref) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", ref));
		execSimpleResultTask(new ZapRequestHandler(), params);
	}

	/**
	 * @param progress
	 */
	protected void updateProgress(String progress) {
		getSherlockActivity().setTitle(progress);
		getSherlockActivity().setProgressBarIndeterminateVisibility(true);
	}

	/**
	 * @param title
	 */
	protected void finishProgress(String title) {
		mCurrentTitle = title;
		getSherlockActivity().setTitle(genWindowTitle(title));
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
	}

	/**
	 * @return
	 */
	protected String genWindowTitle(String title) {
		return title;
	}

	/**
	 * @param success
	 * @param result
	 */
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
		String toastText = (String) getText(R.string.get_content_error);
		String stateText = result.getString(SimpleResult.KEY_STATE_TEXT);

		if (stateText != null && !"".equals(stateText)) {
			toastText = stateText;
		} else if (mShc.hasError()) {
			toastText = mShc.getErrorText();
		}

		showToast(toastText);
	}

	/**
	 * @param title
	 * @param list
	 */
	protected void finishListProgress(String title, ArrayList<ExtendedHashMap> list) {
		finishProgress(title);
		setEmptyText(getText(R.string.no_list_item));

		mMapList.clear();
		mMapList.addAll(list);
		mAdapter.notifyDataSetChanged();
	}

	/**
	 * @param toastText
	 */
	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getSherlockActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param toastText
	 */
	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(getSherlockActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (DreamDroid.getSharedPreferences().getBoolean("volume_control", false)) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				onVolumeButtonClicked(Volume.CMD_UP);
				return true;

			case KeyEvent.KEYCODE_VOLUME_DOWN:
				onVolumeButtonClicked(Volume.CMD_DOWN);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || false;
	}

	/**
	 * Called after a Button has been clicked
	 * 
	 * @param id
	 *            The id of the item
	 * @param longClick
	 *            If true the item has been long-clicked
	 */
	@SuppressWarnings("unchecked")
	private void onVolumeButtonClicked(String set) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("set", set));
		if (mVolumeTask != null) {
			mVolumeTask.cancel(true);
		}

		mVolumeTask = new SetVolumeTask();
		mVolumeTask.execute(params);
	}

	/**
	 * @param success
	 * @param volume
	 */
	private void onVolumeSet(boolean success, ExtendedHashMap volume) {
		String text = getString(R.string.get_content_error);
		if (success) {
			if (Python.TRUE.equals(volume.getString(Volume.KEY_RESULT))) {
				String current = volume.getString(Volume.KEY_CURRENT);
				boolean muted = Python.TRUE.equals(volume.getString(Volume.KEY_MUTED));
				if (muted) {
					text = getString(R.string.current_volume, getString(R.string.muted));
				} else {
					text = getString(R.string.current_volume, current);
				}
			}
		}
		showToast(text);
	}

	/**
	 * If a targetFragment has been set using setTargetFragement() return to it.
	 */
	protected void finish() {
		finish(Statics.RESULT_NONE, null);
	}

	/**
	 * If a targetFragment has been set using setTargetFragement() return to it.
	 * 
	 * @param resultCode
	 */
	protected void finish(int resultCode) {
		finish(resultCode, null);
	}

	protected ArrayList<NameValuePair> getHttpParams() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		return params;
	}

	protected Bundle getLoaderBundle() {
		Bundle args = new Bundle();
		args.putSerializable("params", getHttpParams());
		return args;
	}

	/**
	 * Reloads the EPG information by calling a <code>GetEpgListTask</code>.
	 */
	protected void reload() {
		getSherlockActivity().setProgressBarIndeterminateVisibility(true);
		if (!"".equals(mBaseTitle.trim()))
			mCurrentTitle = mBaseTitle + " - " + getString(R.string.loading);

		getSherlockActivity().setTitle(mCurrentTitle);

		getLoaderManager().restartLoader(0, getLoaderBundle(), this);
	}

	/**
	 * If a targetFragment has been set using setTargetFragement() return to it.
	 * 
	 * @param resultCode
	 * @param data
	 */
	protected void finish(int resultCode, Intent data) {
		SherlockFragment f = (SherlockFragment) getTargetFragment();
		if (f != null) {
			MultiPaneHandler mph = (MultiPaneHandler) getSherlockActivity();
			mph.showDetails(f);
			if (resultCode != Statics.RESULT_NONE || data != null) {
				f.onActivityResult(getTargetRequestCode(), resultCode, data);
			}
		}
	}

	protected String getLoadFinishedTitle() {
		return mBaseTitle;
	}

	@Override
	public void onLoadFinished(Loader<ArrayList<ExtendedHashMap>> loader, ArrayList<ExtendedHashMap> list) {
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);

		mMapList.clear();
		if (list != null) {
			mCurrentTitle = getLoadFinishedTitle();
			getSherlockActivity().setTitle(mCurrentTitle);

			if (list.size() == 0)
				setEmptyText(getText(R.string.no_list_item));
			else
				mMapList.addAll(list);
		} else {
			setEmptyText(getText(R.string.error));
		}
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onLoaderReset(Loader<ArrayList<ExtendedHashMap>> loader) {
	}
}
