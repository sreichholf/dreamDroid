/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.helper;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.EnigmaClient;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.enigma.SimpleResultLoadKt;
import net.reichholf.dreamdroid.enigma.VolumePowerSleepLoadKt;
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment;
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
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

public class HttpFragmentHelper {
    public static final int LOADER_DEFAULT_ID = 0;
    private Fragment mFragment;
    @Nullable
	private SwipeRefreshLayout mSwipeRefreshLayout;
    @Nullable
    private ComposeRefreshState mComposeRefresh;

    protected final String sData = "data";
    protected SimpleHttpClient mShc;
    protected boolean mIsReloading = false;

    @Nullable
    protected Job mSimpleResultJob;
    @Nullable
    protected Job mVolumeJob;

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
        mComposeRefresh = null;
    }

    public void setComposeRefresh(@Nullable ComposeRefreshState composeRefresh) {
        mComposeRefresh = composeRefresh;
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        mSwipeRefreshLayout = view.findViewById(R.id.ptr_layout);
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.setOnRefreshListener((SwipeRefreshLayout.OnRefreshListener) mFragment);
        }
    }

    public void onActivityCreated() {
        if (mSwipeRefreshLayout == null)
            return;

        Context ctx = getAppCompatActivity();
        TypedValue typed_value = new TypedValue();
        ctx.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, typed_value, true);
        mSwipeRefreshLayout.setProgressViewOffset(false, 0, getAppCompatActivity().getResources().getDimensionPixelSize(typed_value.resourceId));

        ctx.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorAccent, typed_value, true);
        int accent = ContextCompat.getColor(ctx, typed_value.resourceId);
        mSwipeRefreshLayout.setColorSchemeColors(accent);
    }

    protected void resetHttpClient() {
        mShc = SimpleHttpClient.getInstance();
    }

    @Nullable
	public AppCompatActivity getAppCompatActivity() {
        return (AppCompatActivity) mFragment.getActivity();
    }

    @NonNull
	public IHttpBase getBaseFragment() {
        return (IHttpBase) mFragment;
    }

    @Nullable
    public String getString(int resId) {
        if (mFragment != null)
            return mFragment.getActivity().getString(resId);
        return null;
    }

    @Nullable
    public Context getContext() {
        return getAppCompatActivity();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(getAppCompatActivity() == null)
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
        if (mSimpleResultJob != null) {
            mSimpleResultJob.cancel(null);
            mSimpleResultJob = null;
        }
        if (mVolumeJob != null) {
            mVolumeJob.cancel(null);
            mVolumeJob = null;
        }
    }

    @SuppressWarnings("unchecked")
    private void onVolumeButtonClicked(String set) {
        ArrayList<NameValuePair> params = new ArrayList<>();
        params.add(new NameValuePair("set", set));
        if (mVolumeJob != null) {
            mVolumeJob.cancel(null);
        }

        mVolumeJob = VolumePowerSleepLoadKt.launchVolumeSetLoad(mFragment, params, (success, volume) -> {
            mVolumeJob = null;
            onVolumeSet(success, volume);
            return Unit.INSTANCE;
        });
    }

    @SuppressWarnings("unchecked")
    public void execSimpleResultTask(SimpleResultRequestHandler handler, ArrayList<NameValuePair> params) {
        if (mSimpleResultJob != null) {
            mSimpleResultJob.cancel(null);
        }
        mSimpleResultJob = SimpleResultLoadKt.launchSimpleResultLoad(mFragment, handler, params, (success, result, http) -> {
            mSimpleResultJob = null;
            onSimpleResult(success, result, http);
            return Unit.INSTANCE;
        });
    }

    private void onSimpleResult(boolean success, @NonNull ExtendedHashMap result, @NonNull SimpleHttpClient http) {
        if (!mFragment.isAdded())
            return;
        String toastText = (String) mFragment.getText(R.string.get_content_error);
        String stateText = result.getString(SimpleResult.KEY_STATE_TEXT);

        if (stateText != null && !"".equals(stateText)) {
            toastText = stateText;
        } else if (http.hasError()) {
            toastText = http.getErrorText(getContext());
        }

        if(mShowToastOnSimpleResult)
            showToast(toastText);
        ((IHttpBase)mFragment).onSimpleResult(success, result);
    }

    public void showToastOnSimpleResult(boolean show) {
        mShowToastOnSimpleResult = show;
    }

    private void onVolumeSet(boolean success, @NonNull ExtendedHashMap volume) {
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

    public void finishProgress(String title) {
        getBaseFragment().setCurrentTitle(title);
        getAppCompatActivity().setTitle(title);
        onLoadFinished();
    }

    public void findSimilarEvents(@NonNull ExtendedHashMap event) {
        String query = event.getString(Event.KEY_EVENT_TITLE);
        Fragment walker = mFragment;
        while (walker != null) {
            if (walker instanceof PhoneNavHostFragment
                    && ((PhoneNavHostFragment) walker).navigateToEpgSearch(query)) {
                return;
            }
            walker = walker.getParentFragment();
        }
        throw new IllegalStateException("EPG search requires PhoneNavHostFragment");
    }

    /**
     * Former LoaderManager.restartLoader entry point. Phone HTTP screens load via coroutines;
     * calling this is a programming error.
     */
    public void reload() {
        throw new IllegalStateException(
                "HttpFragmentHelper.reload() retired; use lifecycleScope / *Load helpers");
    }

    public void reload(int loader) {
        reload();
    }

    public SimpleHttpClient getHttpClient() {
        return mShc;
    }

    @NonNull
    public List<Service> fetchServices(@NonNull List<NameValuePair> params) {
        return fetchServices(mShc, params);
    }

    @NonNull
    public static List<Service> fetchServices(@NonNull SimpleHttpClient shc, @NonNull List<NameValuePair> params) {
        return EnigmaClient.getServicesBlocking(shc, params);
    }

    @NonNull
    public List<net.reichholf.dreamdroid.enigma.Event> fetchEvents(@NonNull List<NameValuePair> params) {
        return fetchEvents(mShc, params);
    }

    @NonNull
    public List<net.reichholf.dreamdroid.enigma.Event> fetchEvents(@NonNull List<NameValuePair> params, @NonNull String uri) {
        return fetchEvents(mShc, params, uri);
    }

    @NonNull
    public static List<net.reichholf.dreamdroid.enigma.Event> fetchEvents(@NonNull SimpleHttpClient shc, @NonNull List<NameValuePair> params) {
        return fetchEvents(shc, params, net.reichholf.dreamdroid.helpers.enigma2.URIStore.EPG_SERVICE);
    }

    @NonNull
    public static List<net.reichholf.dreamdroid.enigma.Event> fetchEvents(@NonNull SimpleHttpClient shc, @NonNull List<NameValuePair> params, @NonNull String uri) {
        return EnigmaClient.getEventsBlocking(shc, params, uri);
    }

    public void onLoadStarted() {
        if (mIsReloading)
            return;
        mIsReloading = true;
        if (mComposeRefresh != null) {
            mComposeRefresh.setRefreshing(true);
        } else if (mSwipeRefreshLayout != null) {
            if (!mSwipeRefreshLayout.isRefreshing())
                mSwipeRefreshLayout.setRefreshing(true);
        }
    }

    public void onLoadFinished() {
        mIsReloading = false;
        if (mComposeRefresh != null) {
            mComposeRefresh.setRefreshing(false);
        } else if (mSwipeRefreshLayout != null) {
            if (mSwipeRefreshLayout.isRefreshing())
                mSwipeRefreshLayout.setRefreshing(false);
        }
    }

    public void onProfileChanged() {
        resetHttpClient();
    }
}
