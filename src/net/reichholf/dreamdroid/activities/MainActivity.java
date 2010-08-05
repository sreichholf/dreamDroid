/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Message;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import android.app.Dialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;

/**
 * @author sreichholf
 * 
 */
public class MainActivity extends AbstractHttpActivity {
	public static final int DIALOG_SEND_MESSAGE_ID = 0;

	public static final int ITEM_TIMER = 0;
	public static final int ITEM_MOVIES = 1;
	public static final int ITEM_SERVICES = 2;
	public static final int ITEM_INFO = 3;
	public static final int ITEM_MESSAGE = 4;
	public static final int ITEM_REMOTE = 5;
	public static final int ITEM_SETTINGS = 6;
	public static final int ITEM_PROFILES = 7;
	public static final int ITEM_CURRENT = 8;
	public static final int ITEM_EPG_SEARCH = 9;
	public static final int ITEM_EXIT = 99;

	private Button mButtonInfo;
	private Button mButtonCurrent;
	private Button mButtonMessage;
	private Button mButtonMovies;
	private Button mButtonServices;
	private Button mButtonTimer;
	private Button mButtonEpgSearch;
	private Button mButtonRemote;
	private ImageButton mButtonExit;
	private AsyncTask<ExtendedHashMap, String, Boolean> mSendMessageTask;

	private class SendMessageTask extends AsyncTask<ExtendedHashMap, String, Boolean> {
		private ExtendedHashMap mResult;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ExtendedHashMap... params) {
			String xml = Message.send(mShc, params[0]);

			if (xml != null) {
				ExtendedHashMap result = Message.parseSimpleResult(xml);

				String stateText = result.getString(SimpleResult.STATE_TEXT);

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
		@Override
		protected void onPostExecute(Boolean result) {
			if (!result) {
				mResult = new ExtendedHashMap();

				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			} else {
				onMessageSent(mResult);
			}

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

		setContentView(R.layout.main);

		mButtonInfo = (Button) findViewById(R.id.ButtonInfo);
		mButtonCurrent = (Button) findViewById(R.id.ButtonCurrent);
		mButtonMessage = (Button) findViewById(R.id.ButtonMessage);
		mButtonMovies = (Button) findViewById(R.id.ButtonMovies);
		mButtonServices = (Button) findViewById(R.id.ButtonServices);
		mButtonTimer = (Button) findViewById(R.id.ButtonTimer);
		mButtonRemote = (Button) findViewById(R.id.ButtonVirtualRemote);
		mButtonEpgSearch = (Button) findViewById(R.id.ButtonEpgSearch);

		mButtonExit = (ImageButton) findViewById(R.id.ButtonExit);

		registerOnClickListener(mButtonInfo, ITEM_INFO);
		registerOnClickListener(mButtonCurrent, ITEM_CURRENT);
		registerOnClickListener(mButtonMessage, ITEM_MESSAGE);
		registerOnClickListener(mButtonMovies, ITEM_MOVIES);
		registerOnClickListener(mButtonServices, ITEM_SERVICES);
		registerOnClickListener(mButtonTimer, ITEM_TIMER);
		registerOnClickListener(mButtonRemote, ITEM_REMOTE);
		registerOnClickListener(mButtonEpgSearch, ITEM_EPG_SEARCH);
		registerOnClickListener(mButtonExit, ITEM_EXIT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, ITEM_SETTINGS, 0, getText(R.string.settings)).setIcon(R.drawable.edit);
		menu.add(0, ITEM_PROFILES, 1, getText(R.string.profiles)).setIcon(android.R.drawable.ic_menu_preferences);

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		onItemClicked(item.getItemId());
		return true;
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
		case DIALOG_SEND_MESSAGE_ID:
			dialog = new Dialog(this);
			dialog.setContentView(R.layout.send_message_dialog);
			dialog.setTitle(R.string.send_message);

			Button buttonCancel = (Button) dialog.findViewById(R.id.ButtonCancel);
			buttonCancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});

			Button buttonSend = (Button) dialog.findViewById(R.id.ButtonSend);
			buttonSend.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					EditText text = (EditText) dialog.findViewById(R.id.EditTextMessage);
					EditText timeout = (EditText) dialog.findViewById(R.id.EditTextTimeout);

					Spinner type = (Spinner) dialog.findViewById(R.id.SpinnerMessageType);
					String t = new Integer(type.getSelectedItemPosition()).toString();

					sendMessage(text.getText().toString(), t, timeout.getText().toString());
				}
			});

			Spinner spinnerType = (Spinner) dialog.findViewById(R.id.SpinnerMessageType);
			spinnerType.setSelection(2);

			break;
		default:
			dialog = null;
		}

		return dialog;
	}

	/**
	 * @param v
	 * @param id
	 */
	private void registerOnClickListener(View v, final int id) {
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
	private void onItemClicked(int id) {
		Intent intent;

		switch (id) {
		case (ITEM_TIMER):
			intent = new Intent(this, TimerListActivity.class);
			this.startActivity(intent);
			break;

		case (ITEM_MOVIES):
			intent = new Intent(this, MovieListActivity.class);
			this.startActivity(intent);
			break;

		case (ITEM_SERVICES):
			intent = new Intent(this, ServiceListActivity.class);
			ExtendedHashMap map = new ExtendedHashMap();
			map.put("reference", "default");

			intent.putExtra(sData, map);
			intent.setAction(Intent.ACTION_VIEW);

			this.startActivity(intent);
			break;

		case (ITEM_INFO):
			intent = new Intent(this, DeviceInfoActivity.class);
			this.startActivity(intent);
			break;

		case (ITEM_CURRENT):
			intent = new Intent(this, CurrentServiceActivity.class);
			this.startActivity(intent);
			break;

		case (ITEM_REMOTE):
			intent = new Intent(this, VirtualRemoteActivity.class);
			this.startActivity(intent);
			break;

		case (ITEM_SETTINGS):
			intent = new Intent(this, DreamDroidPreferenceActivity.class);
			startActivity(intent);
			break;
			
		case (ITEM_PROFILES):
			intent = new Intent(this, ProfileListActivity.class);
			startActivity(intent);
			break;

		case (ITEM_MESSAGE):
			showDialog(DIALOG_SEND_MESSAGE_ID);
			break;

		case (ITEM_EPG_SEARCH):
			onSearchRequested();
			break;

		case (ITEM_EXIT):
			finish();
			break;
		}
	}

	/**
	 * @param text
	 * @param type
	 * @param timeout
	 */
	private void sendMessage(String text, String type, String timeout) {
		if (mSendMessageTask != null) {
			mSendMessageTask.cancel(true);
		}

		ExtendedHashMap msg = new ExtendedHashMap();
		msg.put(Message.TEXT, text);
		msg.put(Message.TYPE, type);
		msg.put(Message.TIMEOUT, timeout);

		mSendMessageTask = new SendMessageTask();
		mSendMessageTask.execute(msg);
	}

	/**
	 * @param result
	 */
	private void onMessageSent(ExtendedHashMap result) {
		String toastText = (String) getText(R.string.get_content_error);
		String stateText = result.getString(SimpleResult.STATE_TEXT);

		if (stateText != null && !"".equals(stateText)) {
			toastText = stateText;
		}

		removeDialog(DIALOG_SEND_MESSAGE_ID);

		showToast(toastText);
	}

}