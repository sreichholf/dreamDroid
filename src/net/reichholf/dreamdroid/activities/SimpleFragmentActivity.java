/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.EpgSearchFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.MultiChoiceDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

/**
 * @author sre
 * 
 */
public class SimpleFragmentActivity extends SherlockFragmentActivity implements MultiPaneHandler,
		ActionDialog.DialogActionListener, MultiChoiceDialog.MultiChoiceDialogListener {
	public static final int MENU_HOME = 89283794;

	private Fragment mFragment;
	private ActivityCallbackHandler mCallBackHandler;

	protected boolean mThemeSet = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (!mThemeSet)
			DreamDroid.setTheme(this);
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setProgressBarIndeterminateVisibility(false);
		if (getSupportActionBar() != null)
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		mFragment = null;
		boolean initFragment = true;
		if (savedInstanceState != null) {
			mFragment = getSupportFragmentManager().findFragmentById(R.id.content);
			initFragment = false;
		}

		Intent intent = getIntent();
		if (Intent.ACTION_SEARCH.equals(intent.getAction()) && savedInstanceState == null) {
			Bundle args = new Bundle();
			args.putString(SearchManager.QUERY, intent.getStringExtra(SearchManager.QUERY));

			if (DreamDroid.search(this, args)) {
				finish();
				return;
			} else {
				mFragment = new EpgSearchFragment();
				mFragment.setArguments(args);
			}
		}
		initViews(initFragment);
	}

	private void initViews(boolean initFragment) {
		setContentView(R.layout.simple_layout);
		if (initFragment) {
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

			if (mFragment == null) {
				Fragment f = null;
				@SuppressWarnings("unchecked")
				Class<Fragment> c = (Class<Fragment>) getIntent().getExtras().get("fragmentClass");
				Bundle args = new Bundle();
				try {
					f = c.newInstance();
					args.putAll(getIntent().getExtras());
					f.setArguments(args);
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				mFragment = f;
			}
			mCallBackHandler = null;
			ft.replace(R.id.content, mFragment, "fragment");
			ft.commit();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void showDetails(Fragment fragment) {
		showDetails(fragment, true);
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
		showDetails(fragmentClass);
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
		Intent intent = new Intent(this, this.getClass());
		intent.putExtra("fragmentClass", fragment.getClass());
		intent.putExtras(fragment.getArguments());

		if (fragment.getTargetRequestCode() > 0) {
			startActivityForResult(intent, fragment.getTargetRequestCode());
		} else {
			startActivity(intent);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mCallBackHandler != null) {
			if (mCallBackHandler.onKeyDown(keyCode, event)) {
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (mCallBackHandler != null) {
			if (mCallBackHandler.onKeyUp(keyCode, event)) {
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean isMultiPane() {
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		mFragment.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onFragmentResume(Fragment fragment) {
		mCallBackHandler = (ActivityCallbackHandler) fragment;
	}

	@Override
	public void onFragmentPause(Fragment fragment) {
		mCallBackHandler = null;
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

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if (mFragment != null)
			((ActionDialog.DialogActionListener) mFragment).onDialogAction(action, details, dialogTag);
	}

	@Override
	public void onMultiChoiceDialogChange(String dialogTag, DialogInterface dialog, int which, boolean isChecked) {
		if (mFragment != null)
			((MultiChoiceDialog.MultiChoiceDialogListener) mFragment).onMultiChoiceDialogChange(dialogTag, dialog,
					which, isChecked);
	}

	@Override
	public void onMultiChoiceDialogFinish(String dialogTag, int result) {
		if (mFragment != null)
			((MultiChoiceDialog.MultiChoiceDialogListener) mFragment).onMultiChoiceDialogFinish(dialogTag, result);
	}
}
