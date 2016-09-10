/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.enigma2.Remote;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler;

import java.util.ArrayList;

/**
 * A Virtual dreambox remote control using http-requests to send key-strokes
 *
 * @author sreichholf
 *
 */
public class VirtualRemoteFragment extends BaseHttpFragment {
	private static String TAG = VirtualRemoteFragment.class.getSimpleName();

	public static final int[][] REMOTE_BUTTONS = { { R.id.ButtonPower, Remote.KEY_POWER },
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
	private ScreenShotFragment mScreenshotFragment;
	private Vibrator mVibrator;
	private Handler mHandler;
	private Runnable mScreenShotCallback;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mShouldRetainInstance = false;
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.virtual_remote));
		mHttpHelper.showToastOnSimpleResult(false);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		mQuickZap = getArguments().getBoolean(DreamDroid.PREFS_KEY_QUICKZAP, prefs.getBoolean(DreamDroid.PREFS_KEY_QUICKZAP, false) );
		mSimpleRemote = DreamDroid.getCurrentProfile().isSimpleRemote();
		mVibrator = (Vibrator) getAppCompatActivity().getSystemService(Context.VIBRATOR_SERVICE);
		mHandler = new Handler();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = getRemoteView();

		if(view.findViewById(R.id.screenshot_frame) != null){
			mScreenshotFragment = new ScreenShotFragment(false, false, false);

			FragmentManager fm = getChildFragmentManager();
			FragmentTransaction ft = fm.beginTransaction();
			ft.replace(R.id.screenshot_frame, mScreenshotFragment);
			ft.commit();
		} else {
			mScreenshotFragment = null;
		}

		return view;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(mScreenshotFragment != null)
            return mScreenshotFragment.onOptionsItemSelected(item);
        return false;
    }

	@Override
	public void onPause(){
		abortScreenshotReload();
		super.onPause();
	}

	/**
	 * @param buttonmap
	 *            array of (button view id, command id) to register callbacks
	 *            for
	 */
	private void registerButtons(View view, int[][] buttonmap) {
		for (int[] aButtonmap : buttonmap) {
			View v = view.findViewById(aButtonmap[0]);
			if (v == null)
				continue;
			registerOnClickListener(v, aButtonmap[1]);
		}
	}

	/**
	 * Apply Gui-Element-Attributes and register OnClickListeners in dependence
	 * of the active layout (Standard or QuickZap)
	 */
	private View getRemoteView() {
		LayoutInflater inflater = getAppCompatActivity().getLayoutInflater();
		View view;
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
		registerButtons(view, REMOTE_BUTTONS);
		getAppCompatActivity().setTitle(mBaseTitle);

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

		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("command", String.valueOf(id)));
		if (mSimpleRemote) {
			params.add(new NameValuePair("rcu", "standard"));
		} else {
			params.add(new NameValuePair("rcu", "advanced"));
		}
		if (longClick) {
			params.add(new NameValuePair("type", Remote.CLICK_TYPE_LONG));
		}
		execSimpleResultTask(new RemoteCommandRequestHandler(), params);
	}

	private void abortScreenshotReload(){
		if(mScreenShotCallback != null)
			mHandler.removeCallbacks(mScreenShotCallback);
	}

	public void reloadScreenhot(){
		Log.w(TAG, "Scheduling screenshot reload");
		abortScreenshotReload();
		if(mScreenshotFragment == null || !mScreenshotFragment.isVisible() || mScreenShotCallback != null)
			return;

		mScreenShotCallback = new Runnable() {
			@Override
			public void run() {
				Log.w(TAG, "Reloading screenshot");
				mScreenshotFragment.reload();
                mScreenShotCallback = null;
			}
		};
		mHandler.postDelayed(mScreenShotCallback, 700);
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
			toastText = toastText + "\n" + getHttpClient().getErrorText(getContext());
			hasError = true;
		} else if (Python.FALSE.equals(state)) {
			hasError = true;
			toastText = stateText;
		}

		if (hasError) {
			showToast(toastText);
		} else {
			reloadScreenhot();
		}
	}
}
