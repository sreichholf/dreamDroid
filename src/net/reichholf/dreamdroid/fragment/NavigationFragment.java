/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.DreamDroidPreferenceActivity;
import net.reichholf.dreamdroid.activities.FragmentMainActivity;
import net.reichholf.dreamdroid.activities.MediaplayerNavigationActivity;
import net.reichholf.dreamdroid.activities.MovieListActivity;
import net.reichholf.dreamdroid.activities.ServiceListActivity;
import net.reichholf.dreamdroid.activities.TimerListActivity;
import net.reichholf.dreamdroid.adapter.NavigationListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.helpers.DialogHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Message;
import net.reichholf.dreamdroid.helpers.enigma2.PowerState;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MessageRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.PowerStateRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SleepTimerRequestHandler;
import net.reichholf.dreamdroid.widget.NumberPicker;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * This is where all begins. It's the "main menu activity" which acts as central
 * navigation instance
 * 
 * @author sreichholf
 * 
 */
public class NavigationFragment extends AbstractHttpListFragment implements ActivityCallbackHandler{
	public static final int EDIT_PROFILES_REQUEST = 0;

	public static final int ITEM_TIMER = 0;
	public static final int ITEM_MOVIES = 1;
	public static final int ITEM_SERVICES = 2;
	public static final int ITEM_INFO = 3;
	public static final int ITEM_MESSAGE = 4;
	public static final int ITEM_REMOTE = 5;
	public static final int ITEM_SETTINGS = 6;
	public static final int ITEM_CURRENT = 8;
	public static final int ITEM_EPG_SEARCH = 9;
	public static final int ITEM_SCREENSHOT = 10;
	public static final int ITEM_TOGGLE_STANDBY = 11;
	public static final int ITEM_RESTART_GUI = 12;
	public static final int ITEM_REBOOT = 13;
	public static final int ITEM_SHUTDOWN = 14;
	public static final int ITEM_POWERSTATE_DIALOG = 15;
	public static final int ITEM_ABOUT = 16;
	public static final int ITEM_CHECK_CONN = 17;
	public static final int ITEM_SLEEPTIMER = 18;
	public static final int ITEM_MEDIA_PLAYER = 19;
	public static final int ITEM_PROFILES = 20;
	
	public static final int[][] MENU_ITEMS = {
		{ ITEM_SERVICES, R.string.services, R.drawable.ic_menu_list },
		{ ITEM_MOVIES, R.string.movies, R.drawable.ic_menu_movie },
		{ ITEM_TIMER, R.string.timer, R.drawable.ic_menu_clock },
		{ ITEM_REMOTE, R.string.virtual_remote, R.drawable.ic_menu_small_tiles },
		{ ITEM_CURRENT, R.string.current_service, R.drawable.ic_menu_help },
		{ ITEM_POWERSTATE_DIALOG, R.string.powercontrol, R.drawable.ic_menu_power_off },
		{ ITEM_MEDIA_PLAYER, R.string.mediaplayer, R.drawable.ic_menu_music },
		{ ITEM_SLEEPTIMER, R.string.sleeptimer, R.drawable.ic_menu_clock },
		{ ITEM_SCREENSHOT, R.string.screenshot, R.drawable.ic_menu_picture },
		{ ITEM_INFO, R.string.device_info, R.drawable.ic_menu_info },
		{ ITEM_MESSAGE, R.string.send_message, R.drawable.ic_menu_mail },
		{ ITEM_ABOUT, R.string.about, R.drawable.ic_menu_help },
		{ ITEM_CHECK_CONN, R.string.check_connectivity, R.drawable.ic_menu_link },
		{ ITEM_SETTINGS, R.string.settings, android.R.drawable.ic_menu_edit },
		{ ITEM_PROFILES, R.string.profiles, R.drawable.ic_menu_list },
	};
	
	private int[] mCurrent;

	private SetPowerStateTask mSetPowerStateTask;
	private SleepTimerTask mSleepTimerTask;

	private ExtendedHashMap mSleepTimer;
	private FragmentMainActivity mActivity;

	/**
	 * <code>AsyncTask</code> to set the powerstate of the target device
	 * 
	 * @author sre
	 * 
	 */
	private class SetPowerStateTask extends AsyncTask<String, String, Boolean> {
		private ExtendedHashMap mResult;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(String... params) {
			PowerStateRequestHandler handler = new PowerStateRequestHandler();
			String xml = handler.get(mShc, PowerState.getStateParams(params[0]));

			if (xml != null) {
				
				ExtendedHashMap result = new ExtendedHashMap();
				handler.parse(xml, result);
				
				mResult = result;
				return true;
			}

			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Boolean result) {
			if (!result || mResult == null) {
				mResult = new ExtendedHashMap();

				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				} else {
					showToast(getText(R.string.get_content_error));
				}
			} else {
				onPowerStateSet((Boolean) mResult.get(PowerState.KEY_IN_STANDBY));
			}

		}
	}

	/**
	 * @author sre
	 * 
	 */
	private class SleepTimerTask extends AsyncTask<ArrayList<NameValuePair>, Void, Boolean> {
		private ExtendedHashMap mResult;
		private SleepTimerRequestHandler mHandler;
		private boolean mDialogOnFinish;

		public SleepTimerTask(boolean dialogOnFinish) {
			mHandler = new SleepTimerRequestHandler();
			mDialogOnFinish = dialogOnFinish;
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
				ExtendedHashMap result = new ExtendedHashMap();
				mHandler.parse(xml, result);

				String enabled = result.getString(SleepTimer.KEY_ENABLED);

				if (enabled != null) {
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
			mActivity.setIsNavgationDialog(true);
			mActivity.showDialog(DialogHelper.DIALOG_SLEEPTIMER_PROGRESS_ID);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			mActivity.removeDialog(DialogHelper.DIALOG_SLEEPTIMER_PROGRESS_ID);

			if (!result || mResult == null) {
				mResult = new ExtendedHashMap();
			}

			onSleepTimerResult(result, mResult, mDialogOnFinish);
		}
	}

	/**
	 * @param time
	 * @param action
	 * @param enabled
	 */
	private void setSleepTimer(String time, String action, boolean enabled) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("cmd", SleepTimer.CMD_SET));
		params.add(new BasicNameValuePair("time", time));
		params.add(new BasicNameValuePair("action", action));

		if (enabled) {
			params.add(new BasicNameValuePair("enabled", Python.TRUE));
		} else {
			params.add(new BasicNameValuePair("enabled", Python.FALSE));
		}

		execSleepTimerTask(params, false);
	}

	/**
	 * 
	 */
	private void getSleepTimer(boolean showDialogOnFinish) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		execSleepTimerTask(params, showDialogOnFinish);

	}

	/**
	 * @param params
	 */
	@SuppressWarnings("unchecked")
	private void execSleepTimerTask(ArrayList<NameValuePair> params, boolean showDialogOnFinish) {
		if (mSleepTimerTask != null) {
			mSleepTimerTask.cancel(true);
		}

		mSleepTimerTask = new SleepTimerTask(showDialogOnFinish);
		mSleepTimerTask.execute(params);
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
		mActivity = (FragmentMainActivity) getActivity();
		mSleepTimer = new ExtendedHashMap();
		setAdapter();		
	}
	
	public void onActivityCreated(Bundle savedInstanceState){		
		super.onActivityCreated(savedInstanceState);
		getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);	
	}
	
	public void onListItemClick(ListView l, View v, int position, long id){
		mCurrent = MENU_ITEMS[position];

		l.setItemChecked(position, true);		
		onItemClicked(mCurrent[0]);
	}
	
	private void setAdapter() {
		mAdapter = new NavigationListAdapter(mActivity, R.layout.nav_list_item, MENU_ITEMS);
		setListAdapter(mAdapter);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.add(0, ITEM_SETTINGS, 0, getText(R.string.settings)).setIcon(android.R.drawable.ic_menu_edit);
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

	public Dialog onCreateDialog(int id) {
		final Dialog dialog;

		switch (id) {
		case DialogHelper.DIALOG_SEND_MESSAGE_ID:
			dialog = new Dialog(mActivity);
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

		case DialogHelper.DIALOG_SET_POWERSTATE_ID:
			dialog = new Dialog(mActivity);
			dialog.setContentView(R.layout.powercontrol);
			dialog.setTitle(R.string.powercontrol);

			Button buttonToggle = (Button) dialog.findViewById(R.id.ButtonToggle);
			Button buttonGui = (Button) dialog.findViewById(R.id.ButtonGui);
			Button buttonReboot = (Button) dialog.findViewById(R.id.ButtonReboot);
			Button buttonShutdown = (Button) dialog.findViewById(R.id.ButtonShutdown);

			registerOnClickListener(buttonToggle, ITEM_TOGGLE_STANDBY);
			registerOnClickListener(buttonGui, ITEM_RESTART_GUI);
			registerOnClickListener(buttonReboot, ITEM_REBOOT);
			registerOnClickListener(buttonShutdown, ITEM_SHUTDOWN);

			break;
			
		case DialogHelper.DIALOG_ABOUT_ID:
			dialog = new Dialog(mActivity);
			dialog.setContentView(R.layout.about);
			dialog.setTitle(R.string.about);			
			
			TextView aboutText = (TextView) dialog.findViewById(R.id.TextViewAbout);
			CharSequence text = DreamDroid.VERSION_STRING + "\n\n" + getText(R.string.license) + "\n\n"
					+ getText(R.string.source_code_link);
			aboutText.setText(text);
			
			Button buttonDonate = (Button) dialog.findViewById(R.id.ButtonDonate);
			buttonDonate.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View view) {
					Uri uriUrl = Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=stephan%40reichholf%2enet&item_name=dreamDroid&lc=EN&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donate_SM%2egif%3aNonHosted");
					Intent i = new Intent(Intent.ACTION_VIEW, uriUrl);
					startActivity(i);					
				}				
			});
						
			break;
		
		case DialogHelper.DIALOG_SLEEPTIMER_ID:
			dialog = new Dialog(mActivity);
			dialog.setContentView(R.layout.sleeptimer);
			dialog.setTitle(R.string.sleeptimer);
			final NumberPicker time = (NumberPicker) dialog.findViewById(R.id.NumberPicker);
			final CheckBox enabled = (CheckBox) dialog.findViewById(R.id.CheckBoxEnabled);
			final RadioGroup action = (RadioGroup) dialog.findViewById(R.id.RadioGroupAction);
			
			time.setRange(0, 999);
			
			int min = 90;
			try {
				min = Integer.parseInt(mSleepTimer.getString(SleepTimer.KEY_MINUTES));
			} catch (NumberFormatException nfe){}
			
			boolean enable = Python.TRUE.equals(mSleepTimer.getString(SleepTimer.KEY_ENABLED));
			String act = mSleepTimer.getString(SleepTimer.KEY_ACTION);
			
			time.setCurrent(min);
			enabled.setChecked( enable );
			
			if(SleepTimer.ACTION_SHUTDOWN.equals(act)){
				action.check(R.id.RadioButtonShutdown);
			} else{ 
				action.check(R.id.RadioButtonStandby);
			}
			
			Button buttonCloseSleepTimer = (Button) dialog.findViewById(R.id.ButtonClose);
			buttonCloseSleepTimer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}

			});
			
			Button buttonSaveSleepTimer = (Button) dialog.findViewById(R.id.ButtonSave);
			buttonSaveSleepTimer.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					String t = new Integer(time.getCurrent()).toString();
					int id = action.getCheckedRadioButtonId();
					String a = SleepTimer.ACTION_STANDBY;
					
					if(id == R.id.RadioButtonShutdown){
						a = SleepTimer.ACTION_SHUTDOWN;
					} 
					
					setSleepTimer(t, a, enabled.isChecked());
					dialog.dismiss();
				}
			});
			
			break;
		case DialogHelper.DIALOG_SLEEPTIMER_PROGRESS_ID:
			dialog = ProgressDialog.show(mActivity, getText(R.string.sleeptimer), getText(R.string.loading));
			break;
		default:
			dialog = null;
		}

		return dialog;
	}

	/**
	 * Execute the proper action for a item ID (<code>ITEM_*</code> statics)
	 * 
	 * @param id
	 *            The id used to identify the item clicked (<code>ITEM_*</code>
	 *            statics)
	 */
	protected boolean onItemClicked(int id) {
		Intent intent;

		switch (id) {
		case ITEM_TIMER:
			intent = new Intent(mActivity, TimerListActivity.class);
			startActivity(intent);
			return true;

		case ITEM_MOVIES:
			intent = new Intent(mActivity, MovieListActivity.class);
			startActivity(intent);
			return true;

		case ITEM_SERVICES:
			intent = new Intent(mActivity, ServiceListActivity.class);
			ExtendedHashMap map = new ExtendedHashMap();

			intent.putExtra(sData, map);
			intent.setAction(Intent.ACTION_VIEW);

			startActivity(intent);
			return true;

		case ITEM_INFO:
			mActivity.showDetails(DeviceInfoFragment.class);
			return true;

		case ITEM_CURRENT:
			mActivity.showDetails(CurrentServiceFragment.class);
			return true;

		case ITEM_REMOTE:
			mActivity.showDetails(VirtualRemoteFragment.class);
			return true;

		case ITEM_SETTINGS:
			intent = new Intent(mActivity, DreamDroidPreferenceActivity.class);
			startActivity(intent);
			return true;

		case ITEM_MESSAGE:
			mActivity.setIsNavgationDialog(true);
			mActivity.showDialog(DialogHelper.DIALOG_SEND_MESSAGE_ID);
			return true;

		case ITEM_EPG_SEARCH:
			mActivity.onSearchRequested();
			return true;

		case ITEM_SCREENSHOT:
			mActivity.showDetails(ScreenShotFragment.class);
//			intent = new Intent(mActivity, ScreenShotActivity.class);
//			startActivity(intent);
			return true;

		case ITEM_TOGGLE_STANDBY:
			setPowerState(PowerState.STATE_TOGGLE);
			return true;

		case ITEM_RESTART_GUI:
			setPowerState(PowerState.STATE_GUI_RESTART);
			return true;

		case ITEM_REBOOT:
			setPowerState(PowerState.STATE_SYSTEM_REBOOT);
			return true;

		case ITEM_SHUTDOWN:
			setPowerState(PowerState.STATE_SHUTDOWN);
			return true;

		case ITEM_POWERSTATE_DIALOG:
			mActivity.setIsNavgationDialog(true);
			mActivity.showDialog(DialogHelper.DIALOG_SET_POWERSTATE_ID);
			return true;

		case ITEM_ABOUT:
			mActivity.setIsNavgationDialog(true);
			mActivity.showDialog(DialogHelper.DIALOG_ABOUT_ID);
			return true;

		case ITEM_CHECK_CONN:
//			mParent.checkActiveProfile();
			return true;

		case ITEM_SLEEPTIMER:
			getSleepTimer(true);
			return true;
		
		case ITEM_MEDIA_PLAYER:
			startActivity( new Intent(mActivity, MediaplayerNavigationActivity.class) );
		
		case ITEM_PROFILES:
			mActivity.showDetails(ProfileListFragment.class);
//			startActivity( new Intent(mActivity, ProfileListActivity.class));
		default:
			return super.onItemClicked(id);
		}
	}

	/**
	 * @param state
	 *            The powerstate to set. For example defined in
	 *            <code>helpers.enigma2.PowerState.STATE_*</code>
	 */
	private void setPowerState(String state) {
		if (mSetPowerStateTask != null) {
			mSetPowerStateTask.cancel(true);
		}

		mSetPowerStateTask = new SetPowerStateTask();
		mSetPowerStateTask.execute(state);
	}

	/**
	 * Shows succes/error toasts after power state has been set
	 * 
	 * @param isRunning
	 */
	private void onPowerStateSet(boolean isRunning) {
		if (isRunning) {
			showToast(getString(R.string.is_running));
		} else {
			showToast(getString(R.string.in_standby));
		}
	}

	/**
	 * Send a message to the target device which will be shown on TV
	 * 
	 * @param text
	 *            The message text
	 * @param type
	 *            Type of the message as defined in
	 *            <code>helpers.enigma2.Message.STATE_*</code>
	 * @param timeout
	 *            Timeout for the message, 0 means no timeout will occur
	 */
	private void sendMessage(String text, String type, String timeout) {
		ExtendedHashMap msg = new ExtendedHashMap();
		msg.put(Message.KEY_TEXT, text);
		msg.put(Message.KEY_TYPE, type);
		msg.put(Message.KEY_TIMEOUT, timeout);

		execSimpleResultTask(new MessageRequestHandler(), Message.getParams(msg));
	}

	public void setAvailableFeatures() {
		//TODO Feature Handling
//		if (mExtras) {
//			mButtonSleepTimer.setEnabled(DreamDroid.featureSleepTimer());
//		}
	}

	/**
	 * @param success
	 * @param sleepTimer
	 */
	private void onSleepTimerResult(boolean success, ExtendedHashMap sleepTimer, boolean openDialog) {
		if (success) {
			mSleepTimer = sleepTimer;
			if (openDialog) {
				mActivity.setIsNavgationDialog(true);
				mActivity.showDialog(DialogHelper.DIALOG_SLEEPTIMER_ID);
				return;
			}
			String text = sleepTimer.getString(SleepTimer.KEY_TEXT);
			showToast(text);
		} else {
			showToast(getString(R.string.error));
		}
	}
}