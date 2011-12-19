/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.abstivities;

import java.util.ArrayList;
import java.util.HashMap;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.SearchEpgActivity;
import net.reichholf.dreamdroid.activities.TabbedNavigationActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Volume;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.VolumeRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Toast;

/**
 * @author sreichholf
 * 
 */
public abstract class AbstractHttpActivity extends Activity {
	public static final int MENU_HOME = 89283794;

	protected SimpleHttpClient mShc;
	protected final String sData = "data";
	protected SimpleResultTask mSimpleResultTask;
	protected SetVolumeTask mVolumeTask;
	
	protected class SimpleResultTask extends AsyncTask<ArrayList<NameValuePair>, Void, Boolean> {
		private ExtendedHashMap mResult;
		private SimpleResultRequestHandler mHandler;
		
		public SimpleResultTask(SimpleResultRequestHandler handler){
			mHandler = handler;
		}
		
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
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

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(Void... progress) {
			setProgressBarIndeterminateVisibility(true);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			setProgressBarIndeterminateVisibility(false);
			
			if (!result || mResult == null) {
				mResult = new ExtendedHashMap();
			}
			
			onSimpleResult(result, mResult);
		}
	}
	
	protected class SetVolumeTask extends AsyncTask<ArrayList<NameValuePair>, Void, Boolean> {
		private ExtendedHashMap mVolume;
		private VolumeRequestHandler mHandler;
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			publishProgress();
			mHandler = new VolumeRequestHandler();
			String xml = mHandler.get(mShc, params[0]);

			if (xml != null) {
				ExtendedHashMap volume = new ExtendedHashMap();
				mHandler.parse(xml, volume);

				String current = volume.getString(Volume.KEY_CURRENT);
				if(current != null){
					mVolume = volume;
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
		protected void onProgressUpdate(Void... progress) {
			setProgressBarIndeterminateVisibility(true);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			setProgressBarIndeterminateVisibility(false);
			
			if (!result || mVolume == null) {
				mVolume = new ExtendedHashMap();
			}
			
			onVolumeSet(result, mVolume);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		// CustomExceptionHandler.register(this);
		mShc = null;

		if (savedInstanceState != null) {
			Object retained = getLastNonConfigurationInstance();
			if (retained instanceof HashMap) {
				mShc = (SimpleHttpClient) ((HashMap<String, Object>) retained).get("shc");
			}
		}

		if (mShc == null) {
			setClient();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onRetainNonConfigurationInstance()
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put("shc", mShc);

		return map;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_HOME, 99, getText(R.string.home)).setIcon(android.R.drawable.ic_menu_view);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemClicked(item.getItemId());
	}

	/**
	 * 
	 */
	protected void setClient() {
		mShc = SimpleHttpClient.getInstance();
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
		case MENU_HOME:
			intent = new Intent(this, TabbedNavigationActivity.class);
			startActivity(intent);
			return true;
		default:
			return false;
		}
	}

	/**
	 * @param progress
	 */
	protected void updateProgress(String progress){
		setTitle(progress);
		setProgressBarIndeterminateVisibility(true);
	}
	
	/**
	 * @param event
	 */
	protected void findSimilarEvents(ExtendedHashMap event){
		Intent intent = new Intent(this, SearchEpgActivity.class);
		intent.setAction(Intent.ACTION_SEARCH);
		intent.putExtra(SearchManager.QUERY, event.getString(Event.KEY_EVENT_TITLE));
		startActivity(intent);
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
	 * @param handler
	 * @param params
	 */
	@SuppressWarnings("unchecked")
	public void execSimpleResultTask(SimpleResultRequestHandler handler, ArrayList<NameValuePair> params){
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
	 * @param title
	 */
	protected void finishProgress(String title){
		setTitle(title);
		setProgressBarIndeterminateVisibility(false);
	}
		
	/**
	 * @param toastText
	 */
	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param toastText
	 */
	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if( DreamDroid.getSharedPreferences().getBoolean("volume_control", false) ) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_VOLUME_UP:
					onVolumeButtonClicked(Volume.CMD_UP);
					return true;
	
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					onVolumeButtonClicked(Volume.CMD_DOWN);
					return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || super.onKeyUp(keyCode, event);
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
		params.add( new BasicNameValuePair("set", set) );
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
	private void onVolumeSet(boolean success, ExtendedHashMap volume){		
		String text = getString(R.string.get_content_error);
		if(success){
			if(Python.TRUE.equals( volume.getString(Volume.KEY_RESULT)) ){
				String current = volume.getString(Volume.KEY_CURRENT);
				boolean muted = Python.TRUE.equals( volume.getString(Volume.KEY_MUTED) );
				if(muted){
					text = getString(R.string.current_volume, getString(R.string.muted));					
				} else {
					text = getString(R.string.current_volume, current);
				}
			}
		}
		showToast(text);
	}
}
