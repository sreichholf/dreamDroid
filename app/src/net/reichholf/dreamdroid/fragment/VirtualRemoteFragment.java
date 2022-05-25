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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
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
import java.util.HashMap;
import java.util.Map;

/**
 * A Virtual dreambox remote control using http-requests to send key-strokes
 *
 * @author sreichholf
 */
public class VirtualRemoteFragment extends BaseHttpFragment {
    @NonNull
	private static String TAG = VirtualRemoteFragment.class.getSimpleName();

    private boolean mQuickZap;
    private boolean mPlayButtonAsPlayPause;
    private boolean mSimpleRemote;
    private String mBaseTitle;
    @Nullable
	private ScreenShotFragment mScreenshotFragment;
    private Vibrator mVibrator;
    private Handler mHandler;
    @Nullable
	private Runnable mScreenShotCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mShouldRetainInstance = false;
        super.onCreate(savedInstanceState);
        initTitles(getString(R.string.virtual_remote));
        mHttpHelper.showToastOnSimpleResult(false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
        mQuickZap = getArguments().getBoolean(DreamDroid.PREFS_KEY_QUICKZAP, prefs.getBoolean(DreamDroid.PREFS_KEY_QUICKZAP, false));
        mPlayButtonAsPlayPause = getArguments().getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, prefs.getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false));
        mSimpleRemote = DreamDroid.getCurrentProfile().isSimpleRemote();
        mVibrator = (Vibrator) getAppCompatActivity().getSystemService(Context.VIBRATOR_SERVICE);
        mHandler = new Handler();
    }

    public static Integer[][] getRemoteButtons(boolean isPlayButtonPlayPause) {

        Map<Integer, Integer> buttonMap = new HashMap<>();
        buttonMap.put(R.id.ButtonPower, Remote.KEY_POWER);

        buttonMap.put(R.id.ButtonExit, Remote.KEY_EXIT);
        buttonMap.put(R.id.ButtonVolP, Remote.KEY_VOLP);
        buttonMap.put(R.id.ButtonVolM, Remote.KEY_VOLM);
        buttonMap.put(R.id.ButtonMute, Remote.KEY_MUTE);
        buttonMap.put(R.id.ButtonBouP, Remote.KEY_BOUP);
        buttonMap.put(R.id.ButtonBouM, Remote.KEY_BOUM);
        buttonMap.put(R.id.ButtonUp, Remote.KEY_UP);
        buttonMap.put(R.id.ButtonDown, Remote.KEY_DOWN);
        buttonMap.put(R.id.ButtonLeft, Remote.KEY_LEFT);
        buttonMap.put(R.id.ButtonRight, Remote.KEY_RIGHT);
        buttonMap.put(R.id.ButtonOk, Remote.KEY_OK);
        buttonMap.put(R.id.ButtonInfo, Remote.KEY_INFO);
        buttonMap.put(R.id.ButtonMenu, Remote.KEY_MENU);
        buttonMap.put(R.id.ButtonHelp, Remote.KEY_HELP);
        buttonMap.put(R.id.ButtonPvr, Remote.KEY_PVR);
        buttonMap.put(R.id.ButtonRed, Remote.KEY_RED);
        buttonMap.put(R.id.ButtonGreen, Remote.KEY_GREEN);
        buttonMap.put(R.id.ButtonYellow, Remote.KEY_YELLOW);
        buttonMap.put(R.id.ButtonBlue, Remote.KEY_BLUE);
        buttonMap.put(R.id.ButtonRwd, Remote.KEY_REWIND);
        if (isPlayButtonPlayPause) {
            buttonMap.put(R.id.ButtonPlayPause, Remote.KEY_PLAYPAUSE);
        } else {
            buttonMap.put(R.id.ButtonPlay, Remote.KEY_PLAY);
        }
        buttonMap.put(R.id.ButtonStop, Remote.KEY_STOP);
        buttonMap.put(R.id.ButtonFwd, Remote.KEY_FORWARD);
        buttonMap.put(R.id.ButtonRec, Remote.KEY_RECORD);
        buttonMap.put(R.id.ButtonAudio, Remote.KEY_AUDIO);
        buttonMap.put(R.id.Button1, Remote.KEY_1);
        buttonMap.put(R.id.Button2, Remote.KEY_2);
        buttonMap.put(R.id.Button3, Remote.KEY_3);
        buttonMap.put(R.id.Button4, Remote.KEY_4);
        buttonMap.put(R.id.Button5, Remote.KEY_5);
        buttonMap.put(R.id.Button6, Remote.KEY_6);
        buttonMap.put(R.id.Button7, Remote.KEY_7);
        buttonMap.put(R.id.Button8, Remote.KEY_8);
        buttonMap.put(R.id.Button9, Remote.KEY_9);
        buttonMap.put(R.id.Button0, Remote.KEY_0);
        buttonMap.put(R.id.ButtonLeftArrow, Remote.KEY_PREV);
        buttonMap.put(R.id.ButtonRightArrow, Remote.KEY_NEXT);
        buttonMap.put(R.id.ButtonTv, Remote.KEY_TV);
        buttonMap.put(R.id.ButtonRadio, Remote.KEY_RADIO);
        buttonMap.put(R.id.ButtonText, Remote.KEY_TEXT);
        return buttonMap.entrySet()
                .stream()
                .map(e -> new Integer[]{e.getKey(), e.getValue()})
                .toArray(Integer[][]::new);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = getRemoteView();

        if (view.findViewById(R.id.screenshot_frame) != null) {
            mScreenshotFragment = new ScreenShotFragment(false, false, false);

            FragmentManager fm = getChildFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.replace(R.id.screenshot_frame, mScreenshotFragment);
            ft.commit();
        } else {
            mScreenshotFragment = null;
        }

        View playButton = view.findViewById(R.id.ButtonPlay);
        if (playButton != null) {
            playButton.setVisibility(mPlayButtonAsPlayPause ? View.INVISIBLE : View.VISIBLE);
        }
        View playPauseButton = view.findViewById(R.id.ButtonPlayPause);
        if (playPauseButton != null) {
            playPauseButton.setVisibility(mPlayButtonAsPlayPause ? View.VISIBLE : View.INVISIBLE);
        }

        return view;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return mScreenshotFragment != null && mScreenshotFragment.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        abortScreenshotReload();
        super.onPause();
    }

    /**
     * @param buttonmap array of (button view id, command id) to register callbacks
     *                  for
     */
    private void registerButtons(@NonNull View view, @NonNull Integer[][] buttonmap) {
        for (Integer[] aButtonmap : buttonmap) {
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
            mBaseTitle = getString(R.string.app_name_release) + "::" + getString(R.string.quickzap);
        } else {
            if (mSimpleRemote)
                view = inflater.inflate(R.layout.virtual_remote_simple, null, false);
            else
                view = inflater.inflate(R.layout.virtual_remote, null, false);
            mBaseTitle = getString(R.string.app_name_release) + "::" + getString(R.string.virtual_remote);
        }
        registerButtons(view, getRemoteButtons(mPlayButtonAsPlayPause));
        getAppCompatActivity().setTitle(mBaseTitle);

        return view;
    }

    /**
     * Registers an OnClickListener for a specific GUI Element. OnClick the
     * function <code>onButtonClicked</code> will be called with the given id
     *
     * @param v  The view to register an OnClickListener for
     * @param id The item ID to register the listener for
     */
    protected void registerOnClickListener(@NonNull View v, final int id) {
        v.setLongClickable(true);

        v.setOnLongClickListener(v12 -> {
            onButtonClicked(id, true);
            return true;
        });

        v.setOnClickListener(v1 -> onButtonClicked(id, false));
    }

    /**
     * Called after a Button has been clicked
     *
     * @param id        The id of the item
     * @param longClick If true the item has been long-clicked
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

    private void abortScreenshotReload() {
        if (mScreenShotCallback != null)
            mHandler.removeCallbacks(mScreenShotCallback);
    }

    public void reloadScreenhot() {
        Log.w(TAG, "Scheduling screenshot reload");
        abortScreenshotReload();
        if (mScreenshotFragment == null || !mScreenshotFragment.isVisible() || mScreenShotCallback != null)
            return;

        mScreenShotCallback = () -> {
            Log.w(TAG, "Reloading screenshot");
            mScreenshotFragment.reload();
            mScreenShotCallback = null;
        };
        mHandler.postDelayed(mScreenShotCallback, 700);
    }

    @Override
    public void onSimpleResult(boolean success, @NonNull ExtendedHashMap result) {
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
