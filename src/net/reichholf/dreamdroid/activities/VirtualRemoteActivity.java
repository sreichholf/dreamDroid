/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.Toast;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.AbstractHttpActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Remote;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;

/**
 * @author sreichholf
 * 
 */
public class VirtualRemoteActivity extends AbstractHttpActivity {
	public static final int MENU_LAYOUT = 0;	
	
	private Button mButtonPower;
	private Button mButton1;
	private Button mButton2;
	private Button mButton3;
	private Button mButton4;
	private Button mButton5;
	private Button mButton6;
	private Button mButton7;
	private Button mButton8;
	private Button mButton9;
	private Button mButton0;
	private Button mButtonLeftArrow;
	private Button mButtonRightArrow;
	private Button mButtonExit;
	private Button mButtonVolP;
	private Button mButtonVolM;
	private Button mButtonMute;
	private Button mButtonBouP;
	private Button mButtonBouM;
	private Button mButtonUp;
	private Button mButtonDown;
	private Button mButtonLeft;
	private Button mButtonRight;
	private Button mButtonOk;
	private Button mButtonInfo;
	private Button mButtonMenu;
	private Button mButtonHelp;
	private Button mButtonPvr;
	private Button mButtonRed;
	private Button mButtonGreen;
	private Button mButtonYellow;
	private Button mButtonBlue;
	private Button mButtonRwd;
	private Button mButtonPlay;
	private Button mButtonStop;
	private Button mButtonFwd;
	private Button mButtonTv;
	private Button mButtonRadio;
	private Button mButtonText;
	private Button mButtonRec;
	
	private boolean mQuickZap;
	private SharedPreferences mPrefs;
	private SharedPreferences.Editor mEditor;
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
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mEditor = mPrefs.edit();
		mQuickZap = mPrefs.getBoolean("quickzap", false);
		
		reinit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_LAYOUT, 0, getText(R.string.layout)).setIcon(android.R.drawable.ic_menu_always_landscape_portrait);
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
		case MENU_LAYOUT:
			CharSequence[] actions = { getText(R.string.standard), getText(R.string.quickzap) };

			AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
			adBuilder.setTitle(getText(R.string.choose_layout));
			adBuilder.setItems(actions, new DialogInterface.OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						setLayout(false);						
						break;

					case 1:
						setLayout(true);
						break;
					}									
				}
			});

			AlertDialog alert = adBuilder.create();
			alert.show();
			return true;
		}
		return false;
	}
	
	/**
	 * @param b if true QuickZap Layout will be applied. False = Standard Layout
	 */
	private void setLayout(boolean b){
		if(mQuickZap != b){
			mQuickZap = b;
			mEditor.putBoolean("quickzap", mQuickZap);
			mEditor.commit();
			
			reinit();
		}
	}
	
	
	/**
	 * 
	 */
	private void reinit(){
		if(mQuickZap){
			setContentView(R.layout.virtual_remote_quick_zap);
		} else {
			setContentView(R.layout.virtual_remote);
		}
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		mButtonPower = (Button) findViewById(R.id.ButtonPower);
		mButton1 = (Button) findViewById(R.id.Button1);
		mButton2 = (Button) findViewById(R.id.Button2);
		mButton3 = (Button) findViewById(R.id.Button3);
		mButton4 = (Button) findViewById(R.id.Button4);
		mButton5 = (Button) findViewById(R.id.Button5);
		mButton6 = (Button) findViewById(R.id.Button6);
		mButton7 = (Button) findViewById(R.id.Button7);
		mButton8 = (Button) findViewById(R.id.Button8);
		mButton9 = (Button) findViewById(R.id.Button9);
		mButton0 = (Button) findViewById(R.id.Button0);
		mButtonLeftArrow = (Button) findViewById(R.id.ButtonLeftArrow);
		mButtonRightArrow = (Button) findViewById(R.id.ButtonRightArrow);
		mButtonExit = (Button) findViewById(R.id.ButtonExit);
		mButtonVolP = (Button) findViewById(R.id.ButtonVolP);
		mButtonVolM = (Button) findViewById(R.id.ButtonVolM);
		mButtonMute = (Button) findViewById(R.id.ButtonMute);
		mButtonBouP = (Button) findViewById(R.id.ButtonBouP);
		mButtonBouM = (Button) findViewById(R.id.ButtonBouM);
		mButtonUp = (Button) findViewById(R.id.ButtonUp);
		mButtonDown = (Button) findViewById(R.id.ButtonDown);
		mButtonLeft = (Button) findViewById(R.id.ButtonLeft);
		mButtonRight = (Button) findViewById(R.id.ButtonRight);
		mButtonOk = (Button) findViewById(R.id.ButtonOk);
		mButtonInfo = (Button) findViewById(R.id.ButtonInfo);
		mButtonMenu = (Button) findViewById(R.id.ButtonMenu);
		mButtonHelp = (Button) findViewById(R.id.ButtonHelp);
		mButtonPvr = (Button) findViewById(R.id.ButtonPvr);
		mButtonRed = (Button) findViewById(R.id.ButtonRed);
		mButtonGreen = (Button) findViewById(R.id.ButtonGreen);
		mButtonYellow = (Button) findViewById(R.id.ButtonYellow);
		mButtonBlue = (Button) findViewById(R.id.ButtonBlue);
		mButtonRwd = (Button) findViewById(R.id.ButtonRwd);
		mButtonPlay = (Button) findViewById(R.id.ButtonPlay);
		mButtonStop = (Button) findViewById(R.id.ButtonStop);
		mButtonFwd = (Button) findViewById(R.id.ButtonFwd);
		mButtonTv = (Button) findViewById(R.id.ButtonTv);
		mButtonRadio = (Button) findViewById(R.id.ButtonRadio);
		mButtonText = (Button) findViewById(R.id.ButtonText);
		mButtonRec = (Button) findViewById(R.id.ButtonRec);
		
		registerOnClickListener(mButtonPower, Remote.KEY_POWER);
		registerOnClickListener(mButtonExit, Remote.KEY_EXIT);
		registerOnClickListener(mButtonVolP, Remote.KEY_VOLP);
		registerOnClickListener(mButtonVolM, Remote.KEY_VOLM);
		registerOnClickListener(mButtonMute, Remote.KEY_MUTE);
		registerOnClickListener(mButtonBouP, Remote.KEY_BOUP);
		registerOnClickListener(mButtonBouM, Remote.KEY_BOUM);
		registerOnClickListener(mButtonUp, Remote.KEY_UP);
		registerOnClickListener(mButtonDown, Remote.KEY_DOWN);
		registerOnClickListener(mButtonLeft, Remote.KEY_LEFT);
		registerOnClickListener(mButtonRight, Remote.KEY_RIGHT);
		registerOnClickListener(mButtonOk, Remote.KEY_OK);
		registerOnClickListener(mButtonInfo, Remote.KEY_INFO);
		registerOnClickListener(mButtonMenu, Remote.KEY_MENU);
		registerOnClickListener(mButtonHelp, Remote.KEY_HELP);
		registerOnClickListener(mButtonPvr, Remote.KEY_PVR);
		registerOnClickListener(mButtonRed, Remote.KEY_RED);
		registerOnClickListener(mButtonGreen, Remote.KEY_GREEN);
		registerOnClickListener(mButtonYellow, Remote.KEY_YELLOW);
		registerOnClickListener(mButtonBlue, Remote.KEY_BLUE);

		if(!mQuickZap){			
			registerOnClickListener(mButton1, Remote.KEY_1);
			registerOnClickListener(mButton2, Remote.KEY_2);
			registerOnClickListener(mButton3, Remote.KEY_3);
			registerOnClickListener(mButton4, Remote.KEY_4);
			registerOnClickListener(mButton5, Remote.KEY_5);
			registerOnClickListener(mButton6, Remote.KEY_6);
			registerOnClickListener(mButton7, Remote.KEY_7);
			registerOnClickListener(mButton8, Remote.KEY_8);
			registerOnClickListener(mButton9, Remote.KEY_9);
			registerOnClickListener(mButton0, Remote.KEY_0);
			registerOnClickListener(mButtonLeftArrow, Remote.KEY_PREV);
			registerOnClickListener(mButtonRightArrow, Remote.KEY_NEXT);
			registerOnClickListener(mButtonRwd, Remote.KEY_REWIND);
			registerOnClickListener(mButtonPlay, Remote.KEY_PLAY);
			registerOnClickListener(mButtonStop, Remote.KEY_STOP);
			registerOnClickListener(mButtonFwd, Remote.KEY_FORWARD);
			registerOnClickListener(mButtonTv, Remote.KEY_TV);
			registerOnClickListener(mButtonRadio, Remote.KEY_RADIO);
			registerOnClickListener(mButtonText, Remote.KEY_TEXT);
			registerOnClickListener(mButtonRec, Remote.KEY_RECORD);
		}
	}
	
	/**
	 * @param v
	 * @param id
	 */
	private void registerOnClickListener(View v, final int id) {
		v.setLongClickable(true);
		
		v.setOnLongClickListener(new OnLongClickListener(){
			@Override
			public boolean onLongClick(View v){
				onButtonClicked(id, true);
				return true;
			}
		});
		
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onButtonClicked(id, false);
			}
		});
	}

	/**
	 * @param id
	 */
	private void onButtonClicked(int id, boolean longClick) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("command", new Integer(id).toString()));
		if(longClick){
			params.add(new BasicNameValuePair("type", Remote.CLICK_TYPE_LONG));
		}

		ExtendedHashMap result = null;

		String xml = Remote.sendCommand(mShc, params);
		if (xml != null) {
			result = Remote.parseSimpleResult(xml);
		}

		if (result == null) {
			result = new ExtendedHashMap();
		}
		onCommandSent(result);
	}

	/**
	 * @param result
	 */
	private void onCommandSent(ExtendedHashMap result) {
		String state = result.getString(SimpleResult.STATE);
		String stateText = result.getString(SimpleResult.STATE);

		if (Python.FALSE.equals(state) || mShc.hasError()) {
			String toastText = (String) getText(R.string.get_content_error);

			if (mShc.hasError()) {
				toastText += "\n" + mShc.getErrorText();
			}

			if (stateText != null && !"".equals(stateText)) {
				toastText = stateText;
			}

			Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
			toast.show();
		}

	}
}
