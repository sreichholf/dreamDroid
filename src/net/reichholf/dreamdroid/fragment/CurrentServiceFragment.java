/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.CurrentService;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.CurrentServiceRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Shows some information about the service currently running on TV
 * 
 * @author sreichholf
 * 
 */
public class CurrentServiceFragment extends AbstractHttpFragment {
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
			publishProgress(getString(R.string.fetching_data));

			mCurrent.clear();
			CurrentServiceRequestHandler handler = new CurrentServiceRequestHandler();
			String xml = handler.get(mShc);
			if (xml != null) {
				publishProgress(getString(R.string.parsing));

				if (handler.parse(xml, mCurrent)) {
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
			getSherlockActivity().setTitle(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			String title = null;
			getSherlockActivity().setProgressBarIndeterminateVisibility(false);

			if (result) {
				title = getString(R.string.current_service);
				onCurrentServiceReady();
			} else {
				title = getString(R.string.get_content_error);
				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			getSherlockActivity().setTitle(title);

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
		mCurrentServiceReady = false;
		mCurrentTitle = getString(R.string.current_service);
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.current_service, container, false);

		mServiceName = (TextView) view.findViewById(R.id.service_name);
		mProvider = (TextView) view.findViewById(R.id.provider);
		mNowStart = (TextView) view.findViewById(R.id.event_now_start);
		mNowTitle = (TextView) view.findViewById(R.id.event_now_title);
		mNowDuration = (TextView) view.findViewById(R.id.event_now_duration);
		mNextStart = (TextView) view.findViewById(R.id.event_next_start);
		mNextTitle = (TextView) view.findViewById(R.id.event_next_title);
		mNextDuration = (TextView) view.findViewById(R.id.event_next_duration);
		mStream = (Button) view.findViewById(R.id.ButtonStream);
		mNowLayout = (LinearLayout) view.findViewById(R.id.layout_now);
		mNextLayout = (LinearLayout) view.findViewById(R.id.layout_next);

		registerOnClickListener(mNowLayout, Statics.ITEM_NOW);
		registerOnClickListener(mNextLayout, Statics.ITEM_NEXT);
		registerOnClickListener(mStream, Statics.ITEM_STREAM);

		if (savedInstanceState == null) {
			mCurrent = new ExtendedHashMap();
			reload();
		} else {
			mCurrent = (ExtendedHashMap) savedInstanceState.getParcelable("current");
			mCurrentItem = (ExtendedHashMap) savedInstanceState.getParcelable("currentItem");
			onCurrentServiceReady();
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("currentItem", mCurrentItem);
		outState.putParcelable("current", mCurrent);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.reload, menu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case (Statics.ITEM_RELOAD):
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

		if (mCurrentServiceReady) {
			switch (id) {
			case Statics.ITEM_NOW:
				showEpgDetail(mNow);
				return true;
			case Statics.ITEM_NEXT:
				showEpgDetail(mNext);
				return true;
			case Statics.ITEM_STREAM:
				ref = mService.getString(Service.KEY_REFERENCE);
				if (!"".equals(ref) && ref != null) {
					streamService(ref);
				} else {
					showToast(getText(R.string.not_available));
				}
				return true;
			default:
				return false;
			}
		} else {
			showToast(getText(R.string.not_available));
			return true;
		}
	}

	private void showEpgDetail(ExtendedHashMap event) {
		if (event != null) {
			mCurrentItem = event;
			getSherlockActivity().removeDialog(Statics.DIALOG_EPG_ITEM_ID);
			getSherlockActivity().showDialog(Statics.DIALOG_EPG_ITEM_ID);
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
		getSherlockActivity().setProgressBarIndeterminateVisibility(true);
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
		mService = (ExtendedHashMap) mCurrent.get(CurrentService.KEY_SERVICE);
		ArrayList<ExtendedHashMap> events = (ArrayList<ExtendedHashMap>) mCurrent.get(CurrentService.KEY_EVENTS);
		mNow = events.get(0);
		mNext = events.get(1);

		mServiceName.setText(mService.getString(CurrentService.KEY_SERVICE_NAME));
		mProvider.setText(mService.getString(CurrentService.KEY_SERVICE_PROVIDER));
		// Now
		mNowStart.setText(mNow.getString(Event.KEY_EVENT_START_READABLE));
		mNowTitle.setText(mNow.getString(Event.KEY_EVENT_TITLE));
		mNowDuration.setText(mNow.getString(Event.KEY_EVENT_DURATION_READABLE));
		// Next
		mNextStart.setText(mNext.getString(Event.KEY_EVENT_START_READABLE));
		mNextTitle.setText(mNext.getString(Event.KEY_EVENT_TITLE));
		mNextDuration.setText(mNext.getString(Event.KEY_EVENT_DURATION_READABLE));
	}

	public Dialog onCreateDialog(int id) {
		final Dialog dialog;

		switch (id) {
		case Statics.DIALOG_EPG_ITEM_ID:
			if (mCurrentItem != null) {
				String servicename = mCurrentItem.getString(Event.KEY_SERVICE_NAME);
				String title = mCurrentItem.getString(Event.KEY_EVENT_TITLE);
				String date = mCurrentItem.getString(Event.KEY_EVENT_START_READABLE);
				if (!"N/A".equals(title) && date != null) {
					date = date.concat(" (" + (String) mCurrentItem.getString(Event.KEY_EVENT_DURATION_READABLE) + " "
							+ getText(R.string.minutes_short) + ")");
					String descEx = mCurrentItem.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED);

					dialog = new Dialog(getSherlockActivity());
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

					Button buttonIMDb = (Button) dialog.findViewById(R.id.ButtonImdb);
					buttonIMDb.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							IntentFactory.queryIMDb(getSherlockActivity(), mCurrentItem);
							dialog.dismiss();
						}
					});

					Button buttonSimilar = (Button) dialog.findViewById(R.id.ButtonSimilar);
					buttonSimilar.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							findSimilarEvents(mCurrentItem);
							dialog.dismiss();
						}
					});
				} else {
					// No EPG Information is available!
					AlertDialog.Builder builder = new AlertDialog.Builder(getSherlockActivity());
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
			// dialog = super.onCreateDialog(id);
			dialog = null;
		}

		return dialog;
	}

	/**
	 * @param event
	 */
	protected void setTimerByEventData(ExtendedHashMap event) {
		Timer.editUsingEvent((MultiPaneHandler) getSherlockActivity(), event, this);
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

		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.saving), true);
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
	 *            A ServiceReference
	 */
	private void streamService(String ref) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String uriString = "http://" + DreamDroid.getActiveProfile().getStreamHost().trim() + ":8001/" + ref;
		Log.i(DreamDroid.LOG_TAG, "Streaming URL set to '" + uriString + "'");

		intent.setDataAndType(Uri.parse(uriString), "video/*");

		startActivity(intent);
	}
}
