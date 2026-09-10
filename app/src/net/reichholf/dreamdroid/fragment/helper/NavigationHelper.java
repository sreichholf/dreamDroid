package net.reichholf.dreamdroid.fragment.helper;

import android.content.Context;
import android.os.Bundle;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.MainActivity;
import net.reichholf.dreamdroid.enigma.SimpleResultLoadKt;
import net.reichholf.dreamdroid.enigma.VolumePowerSleepLoadKt;
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment;
import net.reichholf.dreamdroid.ui.about.AboutComposeDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PowerStateDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SendMessageDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SleepTimerDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Message;
import net.reichholf.dreamdroid.helpers.enigma2.PowerState;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MessageRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.ui.drawer.DrawerListState;
import net.reichholf.dreamdroid.ui.drawer.DrawerScreenKt;
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes;

import java.util.ArrayList;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Created by Stephan on 25.12.2015.
 */
public class NavigationHelper {

    @NonNull
	protected static int[] sDialogItemIds = {R.id.menu_navigation_sleeptimer, R.id.menu_navigation_message, R.id.menu_navigation_power, R.id.menu_navigation_about, R.id.menu_navigation_changelog};

	/** Drawer menu ids that open a PhoneNavHost root (no extras). EPG is separate. */
	@NonNull
	private static final SparseArray<String> sNavRootRoutes = new SparseArray<>();

	static {
		sNavRootRoutes.put(R.id.menu_navigation_services, PhoneNavRoutes.HUB);
		sNavRootRoutes.put(R.id.menu_navigation_device_info, PhoneNavRoutes.DEVICE_INFO);
		sNavRootRoutes.put(R.id.menu_navigation_current, PhoneNavRoutes.CURRENT);
		sNavRootRoutes.put(R.id.menu_navigation_remote, PhoneNavRoutes.REMOTE);
		sNavRootRoutes.put(R.id.menu_navigation_settings, PhoneNavRoutes.SETTINGS);
		sNavRootRoutes.put(R.id.menu_navigation_screenshot, PhoneNavRoutes.SCREENSHOT);
		sNavRootRoutes.put(R.id.menu_navigation_profiles, PhoneNavRoutes.PROFILES);
		sNavRootRoutes.put(R.id.menu_navigation_signal, PhoneNavRoutes.SIGNAL);
		sNavRootRoutes.put(R.id.menu_navigation_zap, PhoneNavRoutes.ZAP);
		sNavRootRoutes.put(R.id.menu_navigation_backup, PhoneNavRoutes.BACKUP);
	}

    MainActivity mActivity;
    @Nullable
    protected Job mPowerStateJob;
    @Nullable
    protected Job mSleepTimerJob;
    @Nullable
    protected Job mSimpleResultJob;
    protected SimpleHttpClient mShc;
    protected final DrawerListState mDrawerState;

    protected int mSelectedItemId;

    public NavigationHelper(MainActivity activity, @NonNull DrawerListState drawerState) {
        mActivity = activity;
        mDrawerState = drawerState;
        mSelectedItemId = drawerState.getSelectedItemId();
        ComposeView drawerCompose = activity.findViewById(R.id.drawer_compose);
        if (drawerCompose != null) {
            DrawerScreenKt.bindDrawerScreen(drawerCompose, mDrawerState, itemId -> {
                onNavigationItemClick(itemId);
                return Unit.INSTANCE;
            });
        }
    }

    protected SimpleHttpClient getHttpClient() {
        if (mShc == null)
            mShc = SimpleHttpClient.getInstance();
        return mShc;
    }

    protected MainActivity getMainActivity() {
        return mActivity;
    }

    protected void clearBackStack() {
        // Pop the backstack completely everytime the user navigates "away"
        // Avoid's "stacking" fragments due to back-button behaviour that feels
        // really mysterious
        FragmentManager fm = getMainActivity().getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    /**
     * Open a migrated phone NavHost leaf. If {@link PhoneNavHostFragment} is already the
     * detail pane, navigate in-graph; otherwise mount the host with [route] as start.
     */
    protected void navigatePhoneNavRoot(@NonNull String route) {
        Fragment detail = getMainActivity().getSupportFragmentManager()
                .findFragmentById(R.id.detail_view);
        if (detail instanceof PhoneNavHostFragment
                && ((PhoneNavHostFragment) detail).navigateToRoute(route)) {
            return;
        }
        clearBackStack();
        getMainActivity().showDetails(PhoneNavHostFragment.newInstance(route));
    }

    public void onDestroy() {
        if (mPowerStateJob != null) {
            mPowerStateJob.cancel(null);
            mPowerStateJob = null;
        }
        if (mSleepTimerJob != null) {
            mSleepTimerJob.cancel(null);
            mSleepTimerJob = null;
        }
        if (mSimpleResultJob != null) {
            mSimpleResultJob.cancel(null);
            mSimpleResultJob = null;
        }
    }

    protected CharSequence getText(int resId) {
        return mActivity.getText(resId);
    }

    private void onPowerStateSet(boolean success, @NonNull ExtendedHashMap result, String resultText) {
        if (!success) {
            showToast(resultText);
            return;
        }
        boolean isRunning = (Boolean) result.get(PowerState.KEY_IN_STANDBY);
        if (isRunning) {
            showToast(getString(R.string.is_running));
        } else {
            showToast(getString(R.string.in_standby));
        }
    }

    protected String getString(int resId) {
        return mActivity.getString(resId);
    }

    public void onProfileChanged() {
        mShc = SimpleHttpClient.getInstance();
    }

    protected void setSelectedItem(int itemId) {
        if (isDialogItem(itemId))
            return;
        if (itemId == R.id.menu_navigation_profiles) {
            mDrawerState.clearSelection();
            return;
        }
        mDrawerState.select(itemId);
        mSelectedItemId = itemId;
    }

    public void navigateTo(int itemId) {
        onNavigationItemClick(itemId);
    }

    protected boolean isDialogItem(int itemId) {
        for (int id : sDialogItemIds)
            if (id == itemId)
                return true;
        return false;
    }

    protected boolean onNavigationItemClick(int itemId) {
        setSelectedItem(itemId);

		String navRoot = sNavRootRoutes.get(itemId);
		if (navRoot != null) {
			navigatePhoneNavRoot(navRoot);
			getMainActivity().showContent();
			return true;
		}

        switch (itemId) {
            case R.id.menu_navigation_message:
                getMainActivity().showDialogFragment(SendMessageDialog.newInstance(), "sendmessage_dialog");
                break;

            case Statics.ITEM_TOGGLE_STANDBY:
                setPowerState(PowerState.STATE_TOGGLE);
                break;

            case Statics.ITEM_RESTART_GUI:
                setPowerState(PowerState.STATE_GUI_RESTART);
                break;

            case Statics.ITEM_REBOOT:
                setPowerState(PowerState.STATE_SYSTEM_REBOOT);
                break;

            case Statics.ITEM_SHUTDOWN:
                setPowerState(PowerState.STATE_SHUTDOWN);
                break;

            case R.id.menu_navigation_power:
                getMainActivity().showDialogFragment(
                        PowerStateDialog.newInstance(),
                        "powerstate_dialog");
                break;

            case R.id.menu_navigation_about:
                getMainActivity().showDialogFragment(AboutComposeDialog.newInstance(), "about_dialog");
                break;

            case Statics.ITEM_CHECK_CONN:
                getMainActivity().onProfileChanged(DreamDroid.getCurrentProfile());
                break;

            case R.id.menu_navigation_changelog:
                getMainActivity().showChangeLog(false);
                break;

            case R.id.menu_navigation_sleeptimer:
                getSleepTimer(true);
                break;

            case Statics.ITEM_RELOAD:
                return false;

            case R.id.menu_navigation_epg:
                navigateToEpg();
                break;
        }
        getMainActivity().showContent();
        return !isDialogItem(itemId);
    }

	/**
	 * EPG drawer root needs default bouquet ref/name extras (not a plain route map entry).
	 */
	protected void navigateToEpg() {
		Bundle epgArgs = new Bundle();
		String ref = DreamDroid.getCurrentProfile().getDefaultBouquetTv();
		epgArgs.putString(Event.KEY_SERVICE_REFERENCE, ref);
		String name = DreamDroid.getCurrentProfile().getDefaultBouquetTvName();
		epgArgs.putString(Event.KEY_SERVICE_NAME, name);

		Fragment detail = getMainActivity().getSupportFragmentManager()
				.findFragmentById(R.id.detail_view);
		if (detail instanceof PhoneNavHostFragment
				&& ((PhoneNavHostFragment) detail).navigateToEpg(ref, name)) {
			return;
		}
		clearBackStack();
		getMainActivity().showDetails(
				PhoneNavHostFragment.newInstance(PhoneNavRoutes.EPG, epgArgs));
	}

    /**
     * @param time
     * @param action
     * @param enabled
     */
    public void onSetSleepTimer(String time, String action, boolean enabled) {
        ArrayList<NameValuePair> params = new ArrayList<>();
        params.add(new NameValuePair("cmd", SleepTimer.CMD_SET));
        params.add(new NameValuePair("time", time));
        params.add(new NameValuePair("action", action));

        if (enabled) {
            params.add(new NameValuePair("enabled", Python.TRUE));
        } else {
            params.add(new NameValuePair("enabled", Python.FALSE));
        }

        execSleepTimerTask(params, false);
    }

    /**
     *
     */
    protected void getSleepTimer(boolean showDialogOnFinish) {
        ArrayList<NameValuePair> params = new ArrayList<>();
        execSleepTimerTask(params, showDialogOnFinish);
    }

    private void onSleepTimerSet(boolean success, @NonNull ExtendedHashMap result, boolean openDialog, String errorText) {
        if (success) {
            if (openDialog) {
                getMainActivity().showDialogFragment(SleepTimerDialog.newInstance(result), "sleeptimer_dialog");
                return;
            }
            String text = result.getString(SleepTimer.KEY_TEXT);
            showToast(text);
        } else {
            showToast(getString(R.string.error));
        }
    }

    public void execSimpleResultTask(SimpleResultRequestHandler handler, ArrayList<NameValuePair> params) {
        if (mSimpleResultJob != null) {
            mSimpleResultJob.cancel(null);
        }
        mSimpleResultJob = SimpleResultLoadKt.launchSimpleResultLoad(mActivity, handler, params, (success, result, http) -> {
            mSimpleResultJob = null;
            onSimpleResult(success, result, http);
            return Unit.INSTANCE;
        });
    }

    private void onSimpleResult(boolean success, @NonNull ExtendedHashMap result, @NonNull SimpleHttpClient http) {
        String toastText = getString(R.string.get_content_error);
        String stateText = result.getString(SimpleResult.KEY_STATE_TEXT);

        if (stateText != null && !"".equals(stateText)) {
            toastText = stateText;
        } else if (http.hasError()) {
            toastText = http.getErrorText(getContext());
        }

        showToast(toastText);
    }


    /**
     * @param params
     */
    @SuppressWarnings("unchecked")
    protected void execSleepTimerTask(ArrayList<NameValuePair> params, boolean showDialogOnFinish) {
        if (mSleepTimerJob != null) {
            mSleepTimerJob.cancel(null);
        }

        mSleepTimerJob = VolumePowerSleepLoadKt.launchSleepTimerLoad(
                mActivity,
                params,
                showDialogOnFinish,
                mActivity,
                (success, result, openDialog, errorText) -> {
                    mSleepTimerJob = null;
                    onSleepTimerSet(success, result, openDialog, errorText);
                    return Unit.INSTANCE;
                });
    }

    /**
     * @param state The powerstate to set. For example defined in
     *              <code>helpers.enigma2.PowerState.STATE_*</code>
     */
    protected void setPowerState(String state) {
        if (mPowerStateJob != null) {
            mPowerStateJob.cancel(null);
        }

        mPowerStateJob = VolumePowerSleepLoadKt.launchPowerStateSetLoad(
                mActivity,
                state,
                mActivity,
                (success, result, errorText) -> {
                    mPowerStateJob = null;
                    onPowerStateSet(success, result, errorText);
                    return Unit.INSTANCE;
                });
    }

    /**
     * Send a message to the target device which will be shown on TV
     *
     * @param text    The message text
     * @param type    Type of the message as defined in
     *                <code>helpers.enigma2.Message.STATE_*</code>
     * @param timeout Timeout for the message, 0 means no timeout will occur
     */
    public void onSendMessage(String text, String type, String timeout) {
        ExtendedHashMap msg = new ExtendedHashMap();
        msg.put(Message.KEY_TEXT, text);
        msg.put(Message.KEY_TYPE, type);
        msg.put(Message.KEY_TIMEOUT, timeout);

        execSimpleResultTask(new MessageRequestHandler(), Message.getParams(msg));
    }

    public void setAvailableFeatures() {
        // TODO implement feature-handling for list-navigation
    }

    protected void showToast(String toastText) {
        Toast toast = Toast.makeText(getMainActivity(), toastText, Toast.LENGTH_LONG);
        toast.show();
    }

    public void onDialogAction(int action, Object details, String dialogTag) {
        onNavigationItemClick(action);
    }

    public Context getContext() {
        return getMainActivity();
    }
}
