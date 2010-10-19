package net.reichholf.dreamdroid.abstivities;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.TimerEditActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author sreichholf
 * 
 */
public abstract class AbstractHttpEventListActivity extends AbstractHttpListActivity {
	public static final int DIALOG_EPG_ITEM_ID = 9382893;

	protected String mReference;
	protected String mName;

	protected ProgressDialog mAddTimerProgress;
	protected AsyncTask<ExtendedHashMap, String, Boolean> mAddTimerTask;
	protected ExtendedHashMap mCurrentItem;

	protected class AddTimerTask extends AsyncTask<ExtendedHashMap, String, Boolean> {
		private ExtendedHashMap mResult;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ExtendedHashMap... event) {
			String xml = Timer.addByEventId(mShc, event[0]);

			if (xml != null) {
				ExtendedHashMap result = Timer.parseSimpleResult(xml);

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
			onTimerSet(mResult);
		}
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
		case DIALOG_EPG_ITEM_ID:

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
			break;
		default:
			dialog = super.onCreateDialog(id);
		}

		return dialog;
	}

	/**
	 * @param event
	 */
	protected void setTimerById(ExtendedHashMap event) {
		if (mAddTimerTask != null) {
			mAddTimerTask.cancel(true);
			if (mAddTimerProgress != null) {
				if (mAddTimerProgress.isShowing()) {
					mAddTimerProgress.dismiss();
				}
			}
		}

		mAddTimerProgress = ProgressDialog.show(this, "", getText(R.string.saving), true);

		mAddTimerTask = new AddTimerTask();
		mAddTimerTask.execute(event);
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
	 * @param result
	 */
	protected void onTimerSet(ExtendedHashMap result) {
		mAddTimerProgress.dismiss();

		String toastText = (String) getText(R.string.get_content_error);

		String stateText = result.getString(SimpleResult.STATE_TEXT);

		if (stateText != null && !"".equals(stateText)) {
			toastText = stateText;
		}

		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}

}
