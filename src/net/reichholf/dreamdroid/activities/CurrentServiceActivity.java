/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import android.app.SearchManager;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.CurrentService;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.impl.TimerAddByEventIdRequestHandler;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shows some information about the service currently running on TV
 * 
 * @author sreichholf
 * 
 */
public class CurrentServiceActivity extends AbstractHttpActivity {
	public static final int MENU_RELOAD = 0;
	public static final int ITEM_NOW = 0;
	public static final int ITEM_NEXT = 1;
	public static final int ITEM_STREAM = 2;
	public static final int ITEM_SIMILAR = 3;
	public static final int DIALOG_EPG_ITEM_ID = 9382893;

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
	private Button mStream;
	private Button mSimilar;
	private LinearLayout mNowLayout;
	private LinearLayout mNextLayout;	
	protected ProgressDialog mProgress;
	
	private ExtendedHashMap mService;
	private ExtendedHashMap mNow;
	private ExtendedHashMap mNext;
	private ExtendedHashMap mCurrentItem;
	private boolean mCurrentServiceReady;
	

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
			setProgressBarIndeterminateVisibility(false);
			
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
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		mCurrentServiceReady = false;
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
		
		mStream = (Button) findViewById(R.id.ButtonStream);
		mSimilar = (Button) findViewById(R.id.ButtonSimilar);
		mNowLayout = (LinearLayout) findViewById(R.id.layout_now);
		mNextLayout = (LinearLayout) findViewById(R.id.layout_next);
		
		registerOnClickListener(mNowLayout, ITEM_NOW);
		registerOnClickListener(mNextLayout, ITEM_NEXT);
		registerOnClickListener(mStream, ITEM_STREAM);
		registerOnClickListener(mSimilar, ITEM_SIMILAR);

		reload();
	}	
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putSerializable("currentItem", mCurrentItem);
		super.onSaveInstanceState(outState);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		mCurrentItem = (ExtendedHashMap) savedInstanceState.getSerializable("currentItem");
		super.onRestoreInstanceState(savedInstanceState);
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
	@Override
	protected boolean onItemClicked(int id) {
		String ref;
		
		if(mCurrentServiceReady){
			switch (id) {
			case ITEM_NOW:
				showEpgDetail(mNow);
				return true;
			case ITEM_NEXT:
				showEpgDetail(mNext);
				return true;
			case ITEM_STREAM:
				ref = mService.getString(Service.REFERENCE);
				if(!"".equals(ref) && ref != null){
					streamService(ref);
				} else {
					showToast( getText(R.string.not_available) );
				}
				return true;
			case ITEM_SIMILAR:
				String title = mNow.getString(Event.EVENT_TITLE);
				if(!"N/A".equals(title) && title != null){					
					Intent intent = new Intent(this, SearchEpgActivity.class);
					intent.setAction(Intent.ACTION_SEARCH);
					intent.putExtra(SearchManager.QUERY, title);
					startActivity(intent);
				} else {
					showToast( getText(R.string.not_available) );
				}
				return true;
			default:
				return false;
			}
		} else {
			showToast( getText(R.string.not_available) );
			return true;
		}
	}
	
	private void showEpgDetail(ExtendedHashMap event){
		if(event != null){
			mCurrentItem = event;
			removeDialog(DIALOG_EPG_ITEM_ID);
			showDialog(DIALOG_EPG_ITEM_ID);
		}
	}
	
	/**
	 * Reloads all current service information
	 */
	private void reload() {
		if (mCurrentServiceTask != null) {
			mCurrentServiceTask.cancel(true);
		}
		mCurrentServiceReady = false;
		setProgressBarIndeterminateVisibility(true);
		mCurrentServiceTask = new GetCurrentServiceTask();
		mCurrentServiceTask.execute();
	}

	/**
	 * Called after loading the current service has finished to update the
	 * GUI-Content
	 */
	@SuppressWarnings("unchecked")
	private void onCurrentServiceReady() {
		mCurrentServiceReady = true;
		mService = (ExtendedHashMap) mCurrent.get(CurrentService.SERVICE);
		ArrayList<ExtendedHashMap> events = (ArrayList<ExtendedHashMap>) mCurrent.get(CurrentService.EVENTS);
		mNow = events.get(0);
		mNext = events.get(1);

		mServiceName.setText(mService.getString(CurrentService.SERVICE_NAME));
		mProvider.setText(mService.getString(CurrentService.SERVICE_PROVIDER));
		// Now
		mNowStart.setText(mNow.getString(Event.EVENT_START_READABLE));
		mNowTitle.setText(mNow.getString(Event.EVENT_TITLE));
		mNowDuration.setText(mNow.getString(Event.EVENT_DURATION_READABLE));
		// Next
		mNextStart.setText(mNext.getString(Event.EVENT_START_READABLE));
		mNextTitle.setText(mNext.getString(Event.EVENT_TITLE));
		mNextDuration.setText(mNext.getString(Event.EVENT_DURATION_READABLE));
	}
	
	protected Dialog onCreateDialog(int id) {
		final Dialog dialog;

		switch (id) {
		case DIALOG_EPG_ITEM_ID:
			if(mCurrentItem != null){
				String servicename = mCurrentItem.getString(Event.SERVICE_NAME);
				String title = mCurrentItem.getString(Event.EVENT_TITLE);
				String date = mCurrentItem.getString(Event.EVENT_START_READABLE);
				if (!"N/A".equals(title) && date != null) {
					date = date.concat(" (" + (String) mCurrentItem.getString(Event.EVENT_DURATION_READABLE) + " "
							+ getText(R.string.minutes_short) + ")");
					String descEx = mCurrentItem.getString(Event.EVENT_DESCRIPTION_EXTENDED);
	
					dialog = new Dialog(this);
					dialog.setContentView(R.layout.epg_item_dialog);
					dialog.setTitle(title);
	
					TextView textServiceName = (TextView) dialog.findViewById(R.id.service_name);
					textServiceName.setText(servicename);
	
					TextView textTime = (TextView) dialog.findViewById(R.id.epg_time);
					textTime.setText(date);
	
					TextView textDescEx = (TextView) dialog.findViewById(R.id.epg_description_extended);
					textDescEx.setText(descEx);
	
					Button buttonSetTimer = (Button) dialog.findViewById(R.id.ButtonSetTimer);
					buttonSetTimer.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							setTimerById(mCurrentItem);
							dialog.dismiss();
						}
					});
	
					Button buttonEditTimer = (Button) dialog.findViewById(R.id.ButtonEditTimer);
					buttonEditTimer.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							setTimerByEventData(mCurrentItem);
							dialog.dismiss();
						}
					});
				} else {
					// No EPG Information is available!
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setMessage(R.string.no_epg_available).setCancelable(true)
							.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									dialog.cancel();
								}
							});
					dialog = builder.create();
				}
			} else {
				showToast(getText(R.string.not_available));
				dialog = null;
			}
			break;
		default:
			dialog = super.onCreateDialog(id);
		}

		return dialog;
	}
	
	/**
	 * @param event
	 */
	protected void setTimerByEventData(ExtendedHashMap event) {
		ExtendedHashMap timer = Timer.createByEvent(event);
		ExtendedHashMap data = new ExtendedHashMap();
		data.put("timer", timer);

		Intent intent = new Intent(this, TimerEditActivity.class);
		intent.putExtra(sData, data);
		intent.setAction(DreamDroid.ACTION_NEW);

		this.startActivity(intent);
	}
	/**
	 * @param event
	 */
	protected void setTimerById(ExtendedHashMap event) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(this, "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onSimpleResult
	 * (boolean, net.reichholf.dreamdroid.helpers.ExtendedHashMap)
	 */
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		super.onSimpleResult(success, result);
	}
	
	/**
	 * @param ref
	 * 			A ServiceReference
	 */
	private void streamService(String ref){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String uriString = "http://" + DreamDroid.PROFILE.getStreamHost().trim() + ":8001/" + ref;
		Log.i(DreamDroid.LOG_TAG, "Streaming URL set to '" + uriString + "'");
		
		intent.setDataAndType(Uri.parse(uriString) , "video/*");
		
		startActivity(intent);		
	}
}
