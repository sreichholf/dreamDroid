/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.helper;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.EpgSearchFragment;
import net.reichholf.dreamdroid.fragment.interfaces.HttpBaseFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Volume;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.SearchManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.view.KeyEvent;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;

/**
 * @author sre
 * 
 */
public class DreamDroidHttpFragmentHelper {
	private Fragment mFragment;

	protected final String sData = "data";
	protected SimpleHttpClient mShc;

	public DreamDroidHttpFragmentHelper() {
		setClient();
	}

	public DreamDroidHttpFragmentHelper(Fragment fragment) {
		bindToFragment(fragment);
		setClient();
	}

	public void bindToFragment(Fragment fragment) {
		if (!(fragment instanceof HttpBaseFragment))
			throw new IllegalStateException(getClass().getSimpleName() + " must be attached to a HttpBaseFragment.");
		mFragment = fragment;
	}

	protected void setClient() {
		mShc = SimpleHttpClient.getInstance();
	}

	public SherlockFragmentActivity getSherlockActivity() {
		return (SherlockFragmentActivity) mFragment.getActivity();
	}

	public HttpBaseFragment getBaseFragment() {
		return (HttpBaseFragment) mFragment;
	}

	protected SimpleResultTask mSimpleResultTask;
	protected SetVolumeTask mVolumeTask;

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

		protected void onPostExecute(Boolean result) {
			getSherlockActivity().setProgressBarIndeterminateVisibility(false);

			if (!result || mResult == null) {
				mResult = new ExtendedHashMap();
			}

			getBaseFragment().onSimpleResult(result, mResult);
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

		protected void onPostExecute(Boolean result) {
			getSherlockActivity().setProgressBarIndeterminateVisibility(false);

			if (!result || mVolume == null) {
				mVolume = new ExtendedHashMap();
			}

			onVolumeSet(result, mVolume);
		}
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (PreferenceManager.getDefaultSharedPreferences(getSherlockActivity()).getBoolean("volume_control", false)) {
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

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || false;
	}

	public void onDestroy() {
		if (mSimpleResultTask != null)
			mSimpleResultTask.cancel(true);
		if (mVolumeTask != null)
			mVolumeTask.cancel(true);
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
	 * @param success
	 * @param result
	 */
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		String toastText = (String) mFragment.getText(R.string.get_content_error);
		String stateText = result.getString(SimpleResult.KEY_STATE_TEXT);

		if (stateText != null && !"".equals(stateText)) {
			toastText = stateText;
		} else if (mShc.hasError()) {
			toastText = mShc.getErrorText();
		}

		showToast(toastText);
	}

	/**
	 * @param success
	 * @param volume
	 */
	public void onVolumeSet(boolean success, ExtendedHashMap volume) {
		String text = mFragment.getString(R.string.get_content_error);
		if (success) {
			if (Python.TRUE.equals(volume.getString(Volume.KEY_RESULT))) {
				String current = volume.getString(Volume.KEY_CURRENT);
				boolean muted = Python.TRUE.equals(volume.getString(Volume.KEY_MUTED));
				if (muted) {
					text = mFragment.getString(R.string.current_volume, mFragment.getString(R.string.muted));
				} else {
					text = mFragment.getString(R.string.current_volume, current);
				}
			}
		}
		showToast(text);
	}

	/**
	 * @param toastText
	 */
	public void showToast(String toastText) {
		Toast toast = Toast.makeText(getSherlockActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param toastText
	 */
	public void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(getSherlockActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	public void zapTo(String ref) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", ref));
		execSimpleResultTask(new ZapRequestHandler(), params);
	}

	public void updateProgress(String progress) {
		getBaseFragment().setCurrentTitle(progress);
		getSherlockActivity().setTitle(progress);
		getSherlockActivity().setProgressBarIndeterminateVisibility(true);
	}

	/**
	 * @param title
	 */
	public void finishProgress(String title) {
		getBaseFragment().setCurrentTitle(title);
		getSherlockActivity().setTitle(title);
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
	}

	/**
	 * @param event
	 */
	public void findSimilarEvents(ExtendedHashMap event) {
		EpgSearchFragment f = new EpgSearchFragment();
		Bundle args = new Bundle();
		args.putString(SearchManager.QUERY, event.getString(Event.KEY_EVENT_TITLE));
		f.setArguments(args);

		MultiPaneHandler m = (MultiPaneHandler) getSherlockActivity();
		m.showDetails(f);
	}

	@SuppressWarnings("unchecked")
	public void reload() {
		getSherlockActivity().setProgressBarIndeterminateVisibility(true);
		if (!"".equals(getBaseFragment().getBaseTitle().trim()))
			getBaseFragment().setCurrentTitle(
					getBaseFragment().getBaseTitle() + " - " + mFragment.getString(R.string.loading));

		getSherlockActivity().setTitle(getBaseFragment().getCurrentTitle());
		mFragment.getLoaderManager().restartLoader(0, getBaseFragment().getLoaderBundle(),
				(LoaderCallbacks<LoaderResult<ExtendedHashMap>>) mFragment);
	}

	public SimpleHttpClient getHttpClient() {
		return mShc;
	}
}
