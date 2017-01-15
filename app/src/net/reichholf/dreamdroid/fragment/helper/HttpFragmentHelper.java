/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.helper;

import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.asynctask.SetVolumeTask;
import net.reichholf.dreamdroid.asynctask.SimpleResultTask;
import net.reichholf.dreamdroid.fragment.EpgSearchFragment;
import net.reichholf.dreamdroid.fragment.ScreenShotFragment;
import net.reichholf.dreamdroid.fragment.interfaces.IHttpBase;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Volume;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;

/**
 * @author sre
 */
public class HttpFragmentHelper implements SimpleResultTask.SimpleResultTaskHandler, SetVolumeTask.SetVolumeTaskHandler {
    public static final int LOADER_DEFAULT_ID = 0;
    private Fragment mFragment;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    protected final String sData = "data";
    protected SimpleHttpClient mShc;
    protected boolean mIsReloading = false;

    protected SimpleResultTask mSimpleResultTask;
    protected SetVolumeTask mVolumeTask;

    protected boolean mShowToastOnSimpleResult = true;

    public HttpFragmentHelper() {
        resetHttpClient();
    }

    public HttpFragmentHelper(Fragment fragment) {
        bindToFragment(fragment);
        resetHttpClient();
    }

    public void bindToFragment(Fragment fragment) {
        if (!(fragment instanceof IHttpBase) && !(fragment instanceof ScreenShotFragment))
            throw new IllegalStateException(getClass().getSimpleName() + " must be attached to a HttpBaseFragment.");
        if (!fragment.equals(mFragment)) {
            mFragment = fragment;
        }
        mSwipeRefreshLayout = null;
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.ptr_layout);
        if (mSwipeRefreshLayout != null) {
            // Now setup the SwipeRefreshLayout
            mSwipeRefreshLayout.setOnRefreshListener((SwipeRefreshLayout.OnRefreshListener) mFragment);
        }
    }

    public void onActivityCreated() {
        if (mSwipeRefreshLayout == null)
            return;

        Context ctx = getAppCompatActivity();
        TypedValue typed_value = new TypedValue();
        ctx.getTheme().resolveAttribute(android.support.v7.appcompat.R.attr.actionBarSize, typed_value, true);
        mSwipeRefreshLayout.setProgressViewOffset(false, 0, getAppCompatActivity().getResources().getDimensionPixelSize(typed_value.resourceId));

        ctx.getTheme().resolveAttribute(R.attr.colorAccent, typed_value, true);
        int accent = ContextCompat.getColor(ctx, typed_value.resourceId);
        mSwipeRefreshLayout.setColorSchemeColors(accent);
    }

    protected void resetHttpClient() {
        mShc = SimpleHttpClient.getInstance();
    }

    public AppCompatActivity getAppCompatActivity() {
        return (AppCompatActivity) mFragment.getActivity();
    }

    public IHttpBase getBaseFragment() {
        return (IHttpBase) mFragment;
    }

    @Override
    public String getString(int resId) {
        if (mFragment != null)
            return mFragment.getActivity().getString(resId);
        return null;
    }

    @Override
    public Context getContext() {
        return getAppCompatActivity();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(getAppCompatActivity() == null) //not attached to activity
            return false;
        if (PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity()).getBoolean("volume_control", false)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    onVolumeButtonClicked(Volume.CMD_UP);
                    return true;

                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    onVolumeButtonClicked(Volume.CMD_DOWN);
                    return true;
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
    }

    public void onDestroy() {
        if (mSimpleResultTask != null)
            mSimpleResultTask.cancel(true);
        if (mVolumeTask != null)
            mVolumeTask.cancel(true);
    }

    /**
     * Called after a Button has been clicked
     *
     * @param set value to set
     */
    @SuppressWarnings("unchecked")
    private void onVolumeButtonClicked(String set) {
        ArrayList<NameValuePair> params = new ArrayList<>();
        params.add(new NameValuePair("set", set));
        if (mVolumeTask != null) {
            mVolumeTask.cancel(true);
        }

        mVolumeTask = new SetVolumeTask(this);
        mVolumeTask.execute(params);
    }

    /**
     * @param handler
     * @param params
     */
    @SuppressWarnings("unchecked")
    public void execSimpleResultTask(SimpleResultRequestHandler handler, ArrayList<NameValuePair> params) {
        if (mSimpleResultTask != null) {
            mSimpleResultTask.cancel(true);
        }

        mSimpleResultTask = new SimpleResultTask(handler, this);
        mSimpleResultTask.execute(params);
    }

    @Override
    public void onSimpleResult(boolean success, ExtendedHashMap result) {
        if (!mFragment.isAdded())
            return;
        String toastText = (String) mFragment.getText(R.string.get_content_error);
        String stateText = result.getString(SimpleResult.KEY_STATE_TEXT);

        if (stateText != null && !"".equals(stateText)) {
            toastText = stateText;
        } else if (mShc.hasError()) {
            toastText = mShc.getErrorText(getContext());
        }

        if(mShowToastOnSimpleResult)
            showToast(toastText);
        ((SimpleResultTask.SimpleResultTaskHandler)mFragment).onSimpleResult(success, result);
    }

    public void showToastOnSimpleResult(boolean show) {
        mShowToastOnSimpleResult = show;
    }

    /**
     * @param success
     * @param volume
     */
    public void onVolumeSet(boolean success, ExtendedHashMap volume) {
        if (!mFragment.isAdded())
            return;
        String text = mFragment.getString(R.string.get_content_error);
        if (success) {
            if (Python.TRUE.equals(volume.getString(Volume.KEY_RESULT))) {
                String current = volume.getString(Volume.KEY_CURRENT);
                boolean muted = Python.TRUE.equals(volume.getString(Volume.KEY_MUTED));
                if (muted) {
                    text = mFragment.getString(R.string.current_volume);
                    if (text == null)
                        text = mFragment.getString(R.string.muted);
                } else {
                    text = mFragment.getString(R.string.current_volume, current);
                }
            }
        }
        showToast(text);
    }

    /**
     * @param toastText
     */
    private void showToast(String toastText) {
        Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
        toast.show();
    }

    public void zapTo(String ref) {
        ArrayList<NameValuePair> params = new ArrayList<>();
        params.add(new NameValuePair("sRef", ref));
        execSimpleResultTask(new ZapRequestHandler(), params);
    }

    public void updateProgress(String progress) {
        getBaseFragment().setCurrentTitle(progress);
        getAppCompatActivity().setTitle(progress);
        onLoadStarted();
    }

    /**
     * @param title
     */
    public void finishProgress(String title) {
        getBaseFragment().setCurrentTitle(title);
        getAppCompatActivity().setTitle(title);
        onLoadFinished();
    }

    /**
     * @param event
     */
    public void findSimilarEvents(ExtendedHashMap event) {
        EpgSearchFragment f = new EpgSearchFragment();
        Bundle args = new Bundle();
        args.putString(SearchManager.QUERY, event.getString(Event.KEY_EVENT_TITLE));
        f.setArguments(args);

        MultiPaneHandler m = (MultiPaneHandler) getAppCompatActivity();
        m.showDetails(f, true);
    }

    public void reload() {
        reload(LOADER_DEFAULT_ID);
    }

    public void reload(int loader) {
        onLoadStarted();
        if (!"".equals(getBaseFragment().getBaseTitle().trim()))
            getBaseFragment().setCurrentTitle(mFragment.getString(R.string.loading));

        getAppCompatActivity().setTitle(getBaseFragment().getCurrentTitle());
        mFragment.getLoaderManager().restartLoader(loader, getBaseFragment().getLoaderBundle(loader),
                (LoaderCallbacks<LoaderResult<ExtendedHashMap>>) mFragment);
    }

    public SimpleHttpClient getHttpClient() {
        return mShc;
    }

    public void onLoadStarted() {
        if (mIsReloading)
            return;
        mIsReloading = true;
        //The SDK check is a workaround for broken pull-to-refresh with ActionBarCompat
        if (mSwipeRefreshLayout != null) {
            if (!mSwipeRefreshLayout.isRefreshing())
                mSwipeRefreshLayout.setRefreshing(true);
        }
    }

    public void onLoadFinished() {
        mIsReloading = false;
        if (mSwipeRefreshLayout != null)
            if (mSwipeRefreshLayout.isRefreshing())
                mSwipeRefreshLayout.setRefreshing(false);
    }

    public void onProfileChanged() {
        resetHttpClient();
    }
}
