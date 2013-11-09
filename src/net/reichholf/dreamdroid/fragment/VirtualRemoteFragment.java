/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Remote;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * A Virtual dreambox remote control using http-requests to send key-strokes
 *
 * @author sreichholf
 *
 */
public class VirtualRemoteFragment extends AbstractHttpFragment {

	private Vibrator mVibrator;

	private final int[][] mButtons = { { R.id.ButtonPower, Remote.KEY_POWER },
			{ R.id.ButtonExit, Remote.KEY_EXIT }, { R.id.ButtonVolP, Remote.KEY_VOLP },
			{ R.id.ButtonVolM, Remote.KEY_VOLM }, { R.id.ButtonMute, Remote.KEY_MUTE },
			{ R.id.ButtonBouP, Remote.KEY_BOUP }, { R.id.ButtonBouM, Remote.KEY_BOUM },
			{ R.id.ButtonUp, Remote.KEY_UP }, { R.id.ButtonDown, Remote.KEY_DOWN },
			{ R.id.ButtonLeft, Remote.KEY_LEFT }, { R.id.ButtonRight, Remote.KEY_RIGHT },
			{ R.id.ButtonOk, Remote.KEY_OK }, { R.id.ButtonInfo, Remote.KEY_INFO },
			{ R.id.ButtonMenu, Remote.KEY_MENU }, { R.id.ButtonHelp, Remote.KEY_HELP },
			{ R.id.ButtonPvr, Remote.KEY_PVR }, { R.id.ButtonRed, Remote.KEY_RED },
			{ R.id.ButtonGreen, Remote.KEY_GREEN }, { R.id.ButtonYellow, Remote.KEY_YELLOW },
			{ R.id.ButtonBlue, Remote.KEY_BLUE }, { R.id.ButtonRwd, Remote.KEY_REWIND },
			{ R.id.ButtonPlay, Remote.KEY_PLAY }, { R.id.ButtonStop, Remote.KEY_STOP },
			{ R.id.ButtonFwd, Remote.KEY_FORWARD }, { R.id.ButtonRec, Remote.KEY_RECORD },
			{ R.id.ButtonAudio, Remote.KEY_AUDIO }, { R.id.Button1, Remote.KEY_1 }, { R.id.Button2, Remote.KEY_2 },
			{ R.id.Button3, Remote.KEY_3 }, { R.id.Button4, Remote.KEY_4 }, { R.id.Button5, Remote.KEY_5 },
			{ R.id.Button6, Remote.KEY_6 }, { R.id.Button7, Remote.KEY_7 }, { R.id.Button8, Remote.KEY_8 },
			{ R.id.Button9, Remote.KEY_9 }, { R.id.Button0, Remote.KEY_0 }, { R.id.ButtonLeftArrow, Remote.KEY_PREV },
			{ R.id.ButtonRightArrow, Remote.KEY_NEXT }, { R.id.ButtonTv, Remote.KEY_TV },
			{ R.id.ButtonRadio, Remote.KEY_RADIO }, { R.id.ButtonText, Remote.KEY_TEXT } };

	private boolean mQuickZap;
	private boolean mSimpleRemote;
	private String mBaseTitle;
	private SharedPreferences mPrefs;
	private SharedPreferences.Editor mEditor;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.virtual_remote));

		mPrefs = PreferenceManager.getDefaultSharedPreferences(getActionBarActivity());
		mEditor = mPrefs.edit();
		mQuickZap = mPrefs.getBoolean(DreamDroid.PREFS_KEY_QUICKZAP, false);
		mSimpleRemote = DreamDroid.getCurrentProfile().isSimpleRemote();
		mVibrator = (Vibrator) getActionBarActivity().getSystemService(Context.VIBRATOR_SERVICE);
		mEditor.commit();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return getRemoteView();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.remote, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(Statics.ITEM_LAYOUT).setTitle(mQuickZap ? R.string.standard : R.string.quickzap);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Statics.ITEM_LAYOUT:
			setLayout(!mQuickZap);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	/**
	 * @param b
	 *            if true QuickZap Layout will be applied. False = Standard
	 *            Layout
	 */
	private void setLayout(boolean b) {
		if (mQuickZap != b) {
			mQuickZap = b;
			mEditor.putBoolean(DreamDroid.PREFS_KEY_QUICKZAP, mQuickZap);
			mEditor.commit();

			reinit();
		}
	}

	/**
	 * @param buttonmap
	 *            array of (button view id, command id) to register callbacks
	 *            for
	 */
	private void registerButtons(View view, int[][] buttonmap) {
		for (int i = 0; i < buttonmap.length; i++) {
			View v = view.findViewById(buttonmap[i][0]);
			if(v == null)
				continue;
			Button btn = (Button) v;
			registerOnClickListener(btn, buttonmap[i][1]);
		}
	}

	private void reinit() {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.replace(getId(), new VirtualRemoteFragment());
		ft.commit();
	}

	/**
	 * Apply Gui-Element-Attributes and register OnClickListeners in dependence
	 * of the active layout (Standard or QuickZap)
	 */
	private View getRemoteView() {
		LayoutInflater inflater = getActionBarActivity().getLayoutInflater();
		View view = null;
		if (mQuickZap) {
			view = inflater.inflate(R.layout.virtual_remote_quick_zap, null, false);
			mBaseTitle = getString(R.string.app_name) + "::" + getString(R.string.quickzap);
		} else {
			if (mSimpleRemote)
				view = inflater.inflate(R.layout.virtual_remote_simple, null, false);
			else
				view = inflater.inflate(R.layout.virtual_remote, null, false);
			mBaseTitle = getString(R.string.app_name) + "::" + getString(R.string.virtual_remote);
		}
		registerButtons(view, mButtons);
		getActionBarActivity().setTitle(mBaseTitle);

		return view;
	}

	/**
	 * Registers an OnClickListener for a specific GUI Element. OnClick the
	 * function <code>onButtonClicked</code> will be called with the given id
	 *
	 * @param v
	 *            The view to register an OnClickListener for
	 * @param id
	 *            The item ID to register the listener for
	 */
	protected void registerOnClickListener(View v, final int id) {
		v.setLongClickable(true);

		v.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
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
	 * Called after a Button has been clicked
	 *
	 * @param id
	 *            The id of the item
	 * @param longClick
	 *            If true the item has been long-clicked
	 */
	private void onButtonClicked(int id, boolean longClick) {
		int msec = 25;
		if (longClick) {
			msec = 100;
		}

		mVibrator.vibrate(msec);

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("command", String.valueOf(id)));
		if (mSimpleRemote) {
			params.add(new BasicNameValuePair("rcu", "standard"));
		} else {
			params.add(new BasicNameValuePair("rcu", "advanced"));
		}
		if (longClick) {
			params.add(new BasicNameValuePair("type", Remote.CLICK_TYPE_LONG));
		}
		execSimpleResultTask(new RemoteCommandRequestHandler(), params);
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		boolean hasError = false;
		String toastText = getString(R.string.get_content_error);
		String stateText = result.getString(SimpleResult.KEY_STATE_TEXT);
		String state = result.getString(SimpleResult.KEY_STATE);

		if (stateText == null || "".equals(stateText)) {
			hasError = true;
		}

		if (getHttpClient().hasError()) {
			toastText = toastText + "\n" + getHttpClient().getErrorText();
			hasError = true;
		} else if (Python.FALSE.equals(state)) {
			hasError = true;
			toastText = stateText;
		}

		if (hasError) {
			showToast(toastText);
		}
	}
}
