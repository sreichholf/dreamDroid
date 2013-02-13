/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.reichholf.dreamdroid.ActiveProfileChangedListener;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.EpgSearchFragment;
import net.reichholf.dreamdroid.fragment.NavigationFragment;
import net.reichholf.dreamdroid.fragment.ViewPagerNavigationFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SendMessageDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SleepTimerDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.CheckProfile;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;

/**
 * @author sre
 * 
 */
public class FragmentMainActivity extends SherlockFragmentActivity implements MultiPaneHandler,
		ActiveProfileChangedListener, DreamDroid.EpgSearchListener, ActionDialog.DialogActionListener,
		SleepTimerDialog.SleepTimerDialogActionListener, SendMessageDialog.SendMessageDialogActionListener {

	public static List<String> NAVIGATION_DIALOG_TAGS = Arrays.asList(
			new String[] { 	"about_dialog",
							"powerstate_dialog",
							"sendmessage_dialog",
							"sleeptimer_dialog",
							"sleeptimer_progress_dialog" });

	private boolean mMultiPane;

	// private FragmentManager getSupportFragmentManager();
	private NavigationFragment mNavigationFragment;

	private TextView mActiveProfile;
	private TextView mConnectionState;

	private CheckProfileTask mCheckProfileTask;

	private Fragment mDetailFragment;

	private class CheckProfileTask extends AsyncTask<Void, String, ExtendedHashMap> {
		private Profile mProfile;

		public CheckProfileTask(Profile p) {
			mProfile = p;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected ExtendedHashMap doInBackground(Void... params) {
			publishProgress(getText(R.string.checking).toString());
			return CheckProfile.checkProfile(mProfile);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			setConnectionState(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(ExtendedHashMap result) {
			Log.i(DreamDroid.LOG_TAG, result.toString());
			if ((Boolean) result.get(CheckProfile.KEY_HAS_ERROR)) {
				String error = getString((Integer) result.get(CheckProfile.KEY_ERROR_TEXT));
				setConnectionState(error);
			} else {
				setConnectionState(getString(R.string.ok));
				mNavigationFragment.setAvailableFeatures();
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		setProgressBarIndeterminateVisibility(false);

		if (savedInstanceState != null) {
			mNavigationFragment = (NavigationFragment) getSupportFragmentManager().getFragment(savedInstanceState,
					"navigation");
		}

		DreamDroid.setActiveProfileChangedListener(this);

		initViews();
		mNavigationFragment.setHighlightCurrent(mMultiPane);

		// DreamDroid.registerEpgSearchListener(this);
	}

	private Fragment getCurrentDetailFragment() {
		return mDetailFragment;
	}

	private void initViews() {
		setContentView(R.layout.dualpane);

		if (findViewById(R.id.detail_view) != null) {
			mMultiPane = true;
		} else {
			mMultiPane = false;
		}

		// Force Multipane Layout if User selected the option for it
		if (!mMultiPane && DreamDroid.getSharedPreferences(this).getBoolean("force_multipane", false)) {
			setContentView(R.layout.forced_dualpane);
			mMultiPane = true;
		}

		if (mNavigationFragment == null) {
			if (mMultiPane) {
				mNavigationFragment = new NavigationFragment();
			} else {
				mNavigationFragment = new ViewPagerNavigationFragment();
			}
		} else {
			if (mMultiPane && !mNavigationFragment.getClass().equals(NavigationFragment.class)) {
				mNavigationFragment = new NavigationFragment();
			} else if (!mMultiPane && mNavigationFragment.getClass().equals(NavigationFragment.class)) {
				mNavigationFragment = new ViewPagerNavigationFragment();
			}
		}

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		showFragment(ft, R.id.navigation_view, mNavigationFragment);
		Fragment detailFragment = getCurrentDetailFragment();
		if (detailFragment != null) {
			showFragment(ft, R.id.detail_view, detailFragment);
		}
		ft.commit();

		mActiveProfile = (TextView) findViewById(R.id.TextViewProfile);
		if (mActiveProfile == null) {
			mActiveProfile = new TextView(this);
		}
		mConnectionState = (TextView) findViewById(R.id.TextViewConnectionState);
		if (mConnectionState == null) {
			mConnectionState = new TextView(this);
		}

		onActiveProfileChanged(DreamDroid.getActiveProfile());
	}

	private void showFragment(FragmentTransaction ft, int viewId, Fragment fragment) {
		if (fragment.isAdded()) {
			Log.i(DreamDroid.LOG_TAG, "Fragment already added, showing");
			ft.show(fragment);
		} else {
			Log.i(DreamDroid.LOG_TAG, "Fragment not added, adding");
			ft.replace(viewId, fragment, fragment.getClass().getSimpleName());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#
	 * onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		getSupportFragmentManager().putFragment(outState, "navigation", mNavigationFragment);
		Fragment currentDetailFragment = getCurrentDetailFragment();
		if (currentDetailFragment != null) {
			getSupportFragmentManager().putFragment(outState, "current", currentDetailFragment);
		}
		super.onSaveInstanceState(outState);
	}

	public void onDestroy() {
		DreamDroid.unregisterEpgSearchListener(this);
		super.onDestroy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.OnActiveProfileChangedListener#
	 * onActiveProfileChanged(net.reichholf.dreamdroid.Profile)
	 */
	@Override
	public void onActiveProfileChanged(Profile p) {
		setProfileName();
		if (mCheckProfileTask != null) {
			mCheckProfileTask.cancel(true);
		}

		mCheckProfileTask = new CheckProfileTask(p);
		mCheckProfileTask.execute();
	}

	/**
	 *
	 */
	public void setProfileName() {
		mActiveProfile.setText(DreamDroid.getActiveProfile().getName());
	}

	/**
	 * @param state
	 */
	private void setConnectionState(String state) {
		mConnectionState.setText(state);
		setProgressBarIndeterminateVisibility(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(java
	 * .lang.Class)
	 */
	@Override
	public void showDetails(Class<? extends Fragment> fragmentClass) {
		showDetails(fragmentClass, SimpleFragmentActivity.class);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(java
	 * .lang.Class, java.lang.Class)
	 */
	@Override
	public void showDetails(Class<? extends Fragment> fragmentClass, Class<? extends MultiPaneHandler> handlerClass) {
		try {
			Fragment fragment = fragmentClass.newInstance();
			showDetails(fragment, handlerClass);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android
	 * .support.v4.app.Fragment)
	 */
	@Override
	public void showDetails(Fragment fragment) {
		showDetails(fragment, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android
	 * .support.v4.app.Fragment, java.lang.Class)
	 */
	@Override
	public void showDetails(Fragment fragment, Class<? extends MultiPaneHandler> cls) {
		showDetails(fragment, cls, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android
	 * .support.v4.app.Fragment, boolean)
	 */
	@Override
	public void showDetails(Fragment fragment, boolean addToBackStack) {
		showDetails(fragment, SimpleFragmentActivity.class, addToBackStack);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.MultiPaneHandler#showDetails(android
	 * .support.v4.app.Fragment, java.lang.Class, boolean)
	 */
	@Override
	public void showDetails(Fragment fragment, Class<? extends MultiPaneHandler> cls, boolean addToBackStack) {
		if (mMultiPane) {
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
			ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
			showFragment(ft, R.id.detail_view, fragment);
			if (addToBackStack) {
				ft.addToBackStack(null);
			}
			ft.commit();
		} else {
			Intent intent = new Intent(this, cls);
			intent.putExtra("fragmentClass", fragment.getClass());
			Bundle args = fragment.getArguments();
			if (args != null) {
				intent.putExtras(fragment.getArguments());
			}
			startActivity(intent);
		}
	}

	@Override
	public void setTitle(CharSequence title) {
		if (mMultiPane) {
			TextView t = (TextView) findViewById(R.id.detail_title);
			t.setText(title.toString());
			return;
		}
		super.setTitle(title);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		ActivityCallbackHandler callbackHandler = (ActivityCallbackHandler) getCurrentDetailFragment();
		if (callbackHandler != null)
			if (callbackHandler.onKeyDown(keyCode, event))
				return true;

		// if the detail fragment didn't handle it, check if the navigation
		// fragment wants it
		callbackHandler = (ActivityCallbackHandler) mNavigationFragment;
		if (callbackHandler != null)
			if (callbackHandler.onKeyDown(keyCode, event))
				return true;

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		ActivityCallbackHandler callbackHandler = (ActivityCallbackHandler) getCurrentDetailFragment();
		if (callbackHandler != null)
			if (callbackHandler.onKeyUp(keyCode, event))
				return true;

		// if the detail fragment didn't handle it, check if the navigation
		// fragment wants it
		callbackHandler = (ActivityCallbackHandler) mNavigationFragment;
		if (callbackHandler != null)
			if (callbackHandler.onKeyUp(keyCode, event))
				return true;

		return super.onKeyUp(keyCode, event);
	}

	public boolean isMultiPane() {
		return mMultiPane;
	}

	public void finish(boolean finishFragment) {
		if (mMultiPane && finishFragment) {
			// TODO finish() for Fragment
			// getSupportFragmentManager().popBackStackImmediate();
		} else {
			super.finish();
		}
	}

	@Override
	public void onDetailFragmentResume(Fragment fragment) {
		if (fragment != mNavigationFragment || !mMultiPane)
			mDetailFragment = fragment;
	}

	@Override
	public void onDetailFragmentPause(Fragment fragment) {
		if (fragment != mNavigationFragment || !mMultiPane)
			mDetailFragment = null;
	}

	@Override
	public void onEpgSearch(Bundle args) {
		Fragment f = new EpgSearchFragment();
		f.setArguments(args);
		showDetails(f);
	}

	@Override
	public void showDialogFragment(Class<? extends DialogFragment> fragmentClass, Bundle args, String tag) {
		DialogFragment f = null;
		try {
			f = fragmentClass.newInstance();
			f.setArguments(args);
			showDialogFragment(f, tag);
		} catch (InstantiationException e) {
			Log.e(DreamDroid.LOG_TAG, e.getMessage());
		} catch (IllegalAccessException e) {
			Log.e(DreamDroid.LOG_TAG, e.getMessage());
		}
	}

	@Override
	public void showDialogFragment(DialogFragment fragment, String tag) {
		FragmentManager fm = getSupportFragmentManager();
		fragment.show(fm, tag);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.fragment.dialogs.EpgDetailDialog.
	 * EpgDetailDialogListener#onFinishEpgDetailDialog(int)
	 */
	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if (isNavigationDialog(dialogTag)) {
			((ActionDialog.DialogActionListener) mNavigationFragment).onDialogAction(action, details, dialogTag);
		} else if (mDetailFragment != null) {
			((ActionDialog.DialogActionListener) mDetailFragment).onDialogAction(action, details, dialogTag);
		}
	}

	private boolean isNavigationDialog(String dialogTag) {
		Iterator<String> iter = NAVIGATION_DIALOG_TAGS.iterator();
		while (iter.hasNext()) {
			String tag = iter.next();
			if (tag.equals(dialogTag))
				return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.fragment.dialogs.SleepTimerDialog.
	 * SleepTimerDialogActionListener#setSleepTimer(java.lang.String,
	 * java.lang.String, boolean)
	 */
	@Override
	public void onSetSleepTimer(String time, String action, boolean enabled) {
		if (mNavigationFragment != null)
			((SleepTimerDialog.SleepTimerDialogActionListener) mNavigationFragment).onSetSleepTimer(time, action,
					enabled);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.fragment.dialogs.SendMessageDialog.
	 * SendMessageDialogActionListener#onSendMessage(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public void onSendMessage(String text, String type, String timeout) {
		((SendMessageDialog.SendMessageDialogActionListener) mNavigationFragment).onSendMessage(text, type, timeout);
	}
}
