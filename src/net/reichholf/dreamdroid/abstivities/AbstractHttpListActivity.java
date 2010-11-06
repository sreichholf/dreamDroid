/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.abstivities;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.NameValuePair;

//import net.reichholf.dreamdroid.CustomExceptionHandler;
import net.reichholf.dreamdroid.activities.MainActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.abs.ListRequestHandler;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.SimpleAdapter;
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

	protected ArrayList<ExtendedHashMap> mMapList;
	protected SimpleAdapter mAdapter;
	protected ExtendedHashMap mData;
	protected Bundle mExtras;
	protected String mBaseTitle;
	protected SimpleHttpClient mShc;
	protected final String sData = "data";

	protected abstract class AsyncListUpdateTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean>{
		protected ArrayList<ExtendedHashMap> mTaskList;
		
		protected ListRequestHandler mListRequestHandler;
		protected boolean mRequireLocsAndTags;
		protected ArrayList<String> mLocations;
		protected ArrayList<String> mTags;
		
		public AsyncListUpdateTask(String baseTitle){
			mBaseTitle = getString(R.string.app_name) + "::" + baseTitle;
			mListRequestHandler = null;
		}
		
		public AsyncListUpdateTask(String baseTitle, ListRequestHandler listRequestHandler, boolean requireLocsAndTags){
			mBaseTitle = getString(R.string.app_name) + "::" + baseTitle;
			mListRequestHandler = listRequestHandler;
			mRequireLocsAndTags = requireLocsAndTags;
		}
		
		/* (non-Javadoc)
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			if(mListRequestHandler == null){
				throw new UnsupportedOperationException("Method doInBackground not re-implemented while no ListRequestHandler has been given");
			}
			
			mTaskList = new ArrayList<ExtendedHashMap>();
			publishProgress(mBaseTitle + " - " + getText(R.string.fetching_data));

			String xml = mListRequestHandler.getList(mShc, params);
			if (xml != null) {
				publishProgress(mBaseTitle + " - " + getText(R.string.parsing));

				mTaskList.clear();

				if (mListRequestHandler.parseList(xml, mTaskList)) {
					if(mRequireLocsAndTags){
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
		
		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			updateProgress(progress[0]);
		}
		
		/* (non-Javadoc)
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
			
			if(mRequireLocsAndTags){
				setDefaultLocation();
			}
			finishListProgress(title, mTaskList);
		}
	}
	
	protected void setDefaultLocation(){
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
		
		// CustomExceptionHandler.register(this);
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
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onKeyDown(int, android.view.KeyEvent)
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return super.onKeyDown(keyCode, event);
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
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		final Dialog dialog;

		switch (id) {
		case DIALOG_EMPTY_LIST_ID:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.no_list_item).setCancelable(false)
					.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							return;
						}
					});
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}

		return dialog;
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
			intent = new Intent(this, MainActivity.class);
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
	 * @param title
	 */
	protected void finishProgress(String title){
		setTitle(concatCurrentName(title));
		setProgressBarIndeterminateVisibility(false);
	}
	
	/**
	 * @return
	 */
	protected String concatCurrentName(String title){		
		return title;
	}
	
	/**
	 * @param title
	 * @param list
	 */
	protected void finishListProgress(String title, ArrayList<ExtendedHashMap> list){
		finishProgress(title);
		
		mMapList.clear();
		mMapList.addAll(list);
		mAdapter.notifyDataSetChanged();
		
		if (mMapList.size() == 0) {
			showDialog(DIALOG_EMPTY_LIST_ID);
		}		
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
}
