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
import net.reichholf.dreamdroid.activities.SimpleFragmentActivity;
import net.reichholf.dreamdroid.activities.SimpleNoTitleFragmentActivity;
import net.reichholf.dreamdroid.adapter.NavigationListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;

/**
 * This is where all begins. It's the "main menu activity" which acts as central
 * navigation instance
 * 
 * @author sreichholf
 * 
 */
public class NavigationFragment extends AbstractHttpListFragment{
	// [ ID, string.ID, drawable.ID, Available (1=yes, 0=no), isDialog  (1=yes, 0=no) ] 
	public static final int[][] MENU_ITEMS = {
		{ Statics.ITEM_SERVICES, R.string.services, R.drawable.ic_menu_list, 1, 0 },
		{ Statics.ITEM_MOVIES, R.string.movies, R.drawable.ic_menu_movie, 1, 0},
		{ Statics.ITEM_TIMER, R.string.timer, R.drawable.ic_menu_clock, 1, 0},
		{ Statics.ITEM_EPG_SEARCH, R.string.epg_search, R.drawable.ic_menu_search, 1, 0},
		{ Statics.ITEM_REMOTE, R.string.virtual_remote, R.drawable.ic_menu_small_tiles, 1, 0},
		{ Statics.ITEM_CURRENT, R.string.current_service, R.drawable.ic_menu_help, 1, 0},
		{ Statics.ITEM_POWERSTATE_DIALOG, R.string.powercontrol, R.drawable.ic_menu_power_off, 1, 1 },
//		{ Statics.ITEM_MEDIA_PLAYER, R.string.mediaplayer, R.drawable.ic_menu_music, 1, 0 },
		{ Statics.ITEM_SLEEPTIMER, R.string.sleeptimer, R.drawable.ic_menu_clock, DreamDroid.featureSleepTimer() ? 1: 0 ,1 },
		{ Statics.ITEM_SCREENSHOT, R.string.screenshot, R.drawable.ic_menu_picture, 1, 0 },
		{ Statics.ITEM_INFO, R.string.device_info, R.drawable.ic_menu_info, 1, 0 },
		{ Statics.ITEM_MESSAGE, R.string.send_message, R.drawable.ic_menu_mail, 1, 1 },
		{ Statics.ITEM_ABOUT, R.string.about, R.drawable.ic_menu_help, 1, 1 },		
		{ Statics.ITEM_PROFILES, R.string.profiles, R.drawable.ic_menu_list, 1 ,0 },
	};
	
	private int[] mCurrent;
	private int mCurrentListItem;
	private boolean mHighlightCurrent;

	private ExtendedHashMap mSleepTimer;
	private FragmentMainActivity mActivity;
	
	private SetPowerStateTask mSetPowerStateTask;
	private SleepTimerTask mSleepTimerTask;
	
	public NavigationFragment(){
		super();
		mCurrentTitle = mBaseTitle = "";	
		setHighlightCurrent(true);
	}
	
	/**
	 * <code>AsyncTask</code> to set the powerstate of the target device
	 * 
	 * @author sre
	 * 
	 */
	private class SetPowerStateTask extends AsyncTask<String, String, Boolean> {
		private ExtendedHashMap mResult;

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

		@Override
		protected void onProgressUpdate(Void... progress) {
			getSherlockActivity().showDialog(Statics.DIALOG_SLEEPTIMER_PROGRESS_ID);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			getSherlockActivity().removeDialog(Statics.DIALOG_SLEEPTIMER_PROGRESS_ID);

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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
		
		mCurrentTitle = mBaseTitle = getString(R.string.app_name);
		mActivity = (FragmentMainActivity) getSherlockActivity();
		mSleepTimer = new ExtendedHashMap();
		mCurrentListItem = -1;
		
		setHasOptionsMenu(true);
		setAdapter();
	}
	
	@Override
	public void onDestroy(){
		if(mSleepTimerTask != null)
			mSleepTimerTask.cancel(true);
		if(mSetPowerStateTask != null)
			mSetPowerStateTask.cancel(true);
		super.onDestroy();
	}
	
	public void setHighlightCurrent(boolean highlight){
		mHighlightCurrent = highlight;
	}
	
	public void onActivityCreated(Bundle savedInstanceState){		
		super.onActivityCreated(savedInstanceState);
		getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		getListView().setTextFilterEnabled(true);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id){
		if(mCurrentListItem == position && mActivity.isMultiPane()){
			//Don't reload what we already see
			return;
		}

		mCurrent = (int[]) l.getItemAtPosition(position);

		//only mark the entry if it isn't a "dialog-only-item"
		//TODO find a reliable way to mark the current item...
		if(mHighlightCurrent){
			if(mCurrent[4] == 0){			 
				l.setItemChecked(position, true);
				mCurrentListItem = position;
			} else {
				l.setItemChecked(position, false);
				if(mCurrentListItem > 0){					
					l.setItemChecked(mCurrentListItem, true);
				}
			}
		} else {
			l.setItemChecked(position, false);
		}
		
		onItemClicked(mCurrent[0]);
	}
	
	private void setAdapter() {
		mAdapter = new NavigationListAdapter(mActivity, MENU_ITEMS);
		setListAdapter(mAdapter);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.search, menu);
		inflater.inflate(R.menu.preferences, menu);
	}

	public Dialog onCreateDialog(int id) {
		final Dialog dialog;

		switch (id) {
		case Statics.DIALOG_SEND_MESSAGE_ID:
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
					String t = Integer.valueOf(type.getSelectedItemPosition()).toString();

					sendMessage(text.getText().toString(), t, timeout.getText().toString());
				}
			});

			Spinner spinnerType = (Spinner) dialog.findViewById(R.id.SpinnerMessageType);
			spinnerType.setSelection(2);

			break;

		case Statics.DIALOG_SET_POWERSTATE_ID:
			dialog = new Dialog(mActivity);
			dialog.setContentView(R.layout.powercontrol);
			dialog.setTitle(R.string.powercontrol);

			Button buttonToggle = (Button) dialog.findViewById(R.id.ButtonToggle);
			Button buttonGui = (Button) dialog.findViewById(R.id.ButtonGui);
			Button buttonReboot = (Button) dialog.findViewById(R.id.ButtonReboot);
			Button buttonShutdown = (Button) dialog.findViewById(R.id.ButtonShutdown);

			registerOnClickListener(buttonToggle, Statics.ITEM_TOGGLE_STANDBY);
			registerOnClickListener(buttonGui, Statics.ITEM_RESTART_GUI);
			registerOnClickListener(buttonReboot, Statics.ITEM_REBOOT);
			registerOnClickListener(buttonShutdown, Statics.ITEM_SHUTDOWN);

			break;
			
		case Statics.DIALOG_ABOUT_ID:
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
		
		case Statics.DIALOG_SLEEPTIMER_ID:
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
					String t = Integer.valueOf(time.getCurrent()).toString();
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
		case Statics.DIALOG_SLEEPTIMER_PROGRESS_ID:
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
		case Statics.ITEM_TIMER:
			mActivity.showDetails(TimerListFragment.class);
			return true;

		case Statics.ITEM_MOVIES:
			mActivity.showDetails(MovieListFragment.class);			
			return true;

		case Statics.ITEM_SERVICES:			
			mActivity.showDetails(ServiceListFragment.class);
			return true;

		case Statics.ITEM_INFO:
			mActivity.showDetails(DeviceInfoFragment.class);
			return true;

		case Statics.ITEM_CURRENT:
			mActivity.showDetails(CurrentServiceFragment.class);
			return true;

		case Statics.ITEM_REMOTE:
			mActivity.showDetails(VirtualRemoteFragment.class, SimpleNoTitleFragmentActivity.class);
			return true;

		case Statics.ITEM_PREFERENCES:
			intent = new Intent(mActivity, DreamDroidPreferenceActivity.class);
			startActivity(intent);
			return true;

		case Statics.ITEM_MESSAGE:
			getSherlockActivity().showDialog(Statics.DIALOG_SEND_MESSAGE_ID);
			return true;

		case Statics.ITEM_EPG_SEARCH:
			mActivity.onSearchRequested();
			return true;

		case Statics.ITEM_SCREENSHOT:
			mActivity.showDetails(ScreenShotFragment.class);
			return true;

		case Statics.ITEM_TOGGLE_STANDBY:
			setPowerState(PowerState.STATE_TOGGLE);
			return true;

		case Statics.ITEM_RESTART_GUI:
			setPowerState(PowerState.STATE_GUI_RESTART);
			return true;

		case Statics.ITEM_REBOOT:
			setPowerState(PowerState.STATE_SYSTEM_REBOOT);
			return true;

		case Statics.ITEM_SHUTDOWN:
			setPowerState(PowerState.STATE_SHUTDOWN);
			return true;

		case Statics.ITEM_POWERSTATE_DIALOG:
			getSherlockActivity().showDialog(Statics.DIALOG_SET_POWERSTATE_ID);
			return true;

		case Statics.ITEM_ABOUT:
			getSherlockActivity().showDialog(Statics.DIALOG_ABOUT_ID);
			return true;

		case Statics.ITEM_CHECK_CONN:
			mActivity.onActiveProfileChanged(DreamDroid.getActiveProfile());
			return true;

		case Statics.ITEM_SLEEPTIMER:
			getSleepTimer(true);
			return true;
		
		case Statics.ITEM_MEDIA_PLAYER:
			showToast(getString(R.string.not_implemented));
//			TODO startActivity( new Intent(mActivity, MediaplayerNavigationActivity.class) );
			return true;
		
		case Statics.ITEM_PROFILES:
			mActivity.showDetails(ProfileListFragment.class);
			return true;

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
		//TODO implement feature-handling for list-navigation
	}

	/**
	 * @param success
	 * @param sleepTimer
	 */
	private void onSleepTimerResult(boolean success, ExtendedHashMap sleepTimer, boolean openDialog) {
		if (success) {
			mSleepTimer = sleepTimer;
			if (openDialog) {
				getSherlockActivity().showDialog(Statics.DIALOG_SLEEPTIMER_ID);
				return;
			}
			String text = sleepTimer.getString(SleepTimer.KEY_TEXT);
			showToast(text);
		} else {
			showToast(getString(R.string.error));
		}
	}

	/**
	 * @param textviewprofile
	 * @return
	 */
	public View findViewById(int id) {
		return getSherlockActivity().findViewById(id);
	}
}