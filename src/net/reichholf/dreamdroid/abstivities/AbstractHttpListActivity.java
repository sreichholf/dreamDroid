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
import net.reichholf.dreamdroid.activities.TabbedNavigationActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Volume;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.impl.VolumeRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.impl.ZapRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author sreichholf
 * 
 */

public abstract class AbstractHttpListActivity extends ListActivity {
	// public static ArrayList<ExtendedHashMap> DATA = new
	// ArrayList<ExtendedHashMap>();
	public static final int DIALOG_EMPTY_LIST_ID = 1298032;
	public static final int MENU_HOME = 89283794;

	protected final String sData = "data";
	protected String mBaseTitle;

	protected ArrayList<ExtendedHashMap> mMapList;
	protected ExtendedHashMap mData;
	protected Bundle mExtras;
	protected BaseAdapter mAdapter;
	protected SimpleHttpClient mShc;

	protected TextView mEmpty;
	protected SimpleResultTask mSimpleResultTask;
	protected SetVolumeTask mVolumeTask;

	/**
	 * @author sre
	 * 
	 */
	protected abstract class AsyncListUpdateTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean> {
		protected ArrayList<ExtendedHashMap> mTaskList;

		protected ListRequestHandler mListRequestHandler;
		protected boolean mRequireLocsAndTags;
		protected ArrayList<String> mLocations;
		protected ArrayList<String> mTags;

		public AsyncListUpdateTask(String baseTitle) {
			mBaseTitle = getString(R.string.app_name) + "::" + baseTitle;
			mListRequestHandler = null;
		}

		public AsyncListUpdateTask(String baseTitle, ListRequestHandler listRequestHandler, boolean requireLocsAndTags) {
			mBaseTitle = getString(R.string.app_name) + "::" + baseTitle;
			mListRequestHandler = listRequestHandler;
			mRequireLocsAndTags = requireLocsAndTags;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			if (mListRequestHandler == null) {
				throw new UnsupportedOperationException(
						"Method doInBackground not re-implemented while no ListRequestHandler has been given");
			}

			mTaskList = new ArrayList<ExtendedHashMap>();
			publishProgress(mBaseTitle + " - " + getText(R.string.fetching_data));

			String xml = mListRequestHandler.getList(mShc, params);
			if (xml != null) {
				publishProgress(mBaseTitle + " - " + getText(R.string.parsing));

				mTaskList.clear();

				if (mListRequestHandler.parseList(xml, mTaskList)) {
					if (mRequireLocsAndTags) {
						if (DreamDroid.LOCATIONS.size() == 0) {
							publishProgress(mBaseTitle + " - " + getText(R.string.locations) + " - "
									+ getText(R.string.fetching_data));

							if (!DreamDroid.loadLocations(mShc)) {
								// TODO Add Error-Msg when loadLocations fails
							}
						}

						if (DreamDroid.TAGS.size() == 0) {
							publishProgress(mBaseTitle + " - " + getText(R.string.tags) + " - "
									+ getText(R.string.fetching_data));

							if (!DreamDroid.loadTags(mShc)) {
								// TODO Add Error-Msg when loadTags fails
							}
						}
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
			updateProgress(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Boolean result) {
			String title = null;

			if (result) {
				title = mBaseTitle;
			} else {
				title = mBaseTitle + " - " + getString(R.string.get_content_error);

				if (mShc.hasError()) {
					showToast(getString(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			if (mRequireLocsAndTags) {
				setDefaultLocation();
			}
			finishListProgress(title, mTaskList);
		}
	}

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

	/**
	 * @param event
	 */
	protected void queryImdb(ExtendedHashMap event){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String uriString = "imdb:///find?q=" + event.getString(Event.EVENT_TITLE);
		intent.setData(Uri.parse(uriString));
		try{			
			startActivity(intent);
		} catch(ActivityNotFoundException anfex) {
			uriString = "http://www.imdb.com/find?q=" + event.getString(Event.EVENT_TITLE);
			intent.setData(Uri.parse(uriString));
			startActivity(intent);
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
				ExtendedHashMap volume = mHandler.parse(xml);

				String current = volume.getString(Volume.CURRENT);
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
	protected void setDefaultLocation() {
		throw new UnsupportedOperationException("Required Method setDefaultLocation() not re-implemented");
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

		setContentView(R.layout.list_or_empty);
		mEmpty = (TextView) findViewById(android.R.id.empty);
		mEmpty.setText(R.string.loading);

		mExtras = getIntent().getExtras();
		mMapList = new ArrayList<ExtendedHashMap>();

		if (mExtras != null) {
			HashMap<String, Object> map = (HashMap<String, Object>) mExtras.getSerializable("data");
			if (map != null) {
				mData = new ExtendedHashMap();
				mData.putAll(map);
			}
		} else {
			mExtras = new Bundle();
			getIntent().putExtras(mExtras);
		}

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
		
		getListView().setFastScrollEnabled(true);
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		getIntent().putExtras(mExtras);
		super.onSaveInstanceState(outState);
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
	 * @param dfault
	 * @return
	 */
	public String getDataForKey(String key, String dfault) {
		if (mData != null) {
			String str = (String) mData.get(key);
			if (str != null) {
				return str;
			}
		}
		return dfault;
	}

	/**
	 * @param key
	 * @param dfault
	 * @return
	 */
	public boolean getDataForKey(String key, boolean dfault) {
		if (mData != null) {
			Boolean b = (Boolean) mData.get(key);
			if (b != null) {
				return b.booleanValue();
			}
		}

		return dfault;
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
		setTitle(progress);
		setProgressBarIndeterminateVisibility(true);
	}

	/**
	 * @param title
	 */
	protected void finishProgress(String title) {
		setTitle(genWindowTitle(title));
		setProgressBarIndeterminateVisibility(false);
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
		String stateText = result.getString(SimpleResult.STATE_TEXT);

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
		mEmpty.setText(R.string.no_list_item);

		mMapList.clear();
		mMapList.addAll(list);
		mAdapter.notifyDataSetChanged();
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
		if( DreamDroid.SP.getBoolean("volume_control", false) ) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_VOLUME_UP:
					onVolumeButtonClicked(Volume.COMMAND_UP);
					return true;
	
				case KeyEvent.KEYCODE_VOLUME_DOWN:
					onVolumeButtonClicked(Volume.COMMAND_DOWN);
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
			if(Python.TRUE.equals( volume.getString(Volume.RESULT)) ){
				String current = volume.getString(Volume.CURRENT);
				boolean muted = Python.TRUE.equals( volume.getString(Volume.MUTED) );
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
