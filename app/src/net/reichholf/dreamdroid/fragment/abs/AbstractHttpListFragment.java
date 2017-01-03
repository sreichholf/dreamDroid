/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.MainActivity;
import net.reichholf.dreamdroid.activities.TabbedNavigationActivity;
import net.reichholf.dreamdroid.asynctask.SimpleResultTask;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment;
import net.reichholf.dreamdroid.fragment.interfaces.IHttpBase;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author sreichholf
 */

public abstract class AbstractHttpListFragment extends DreamDroidListFragment implements
		LoaderManager.LoaderCallbacks<LoaderResult<ArrayList<ExtendedHashMap>>>, IHttpBase, IBaseFragment, SwipeRefreshLayout.OnRefreshListener, SimpleResultTask.SimpleResultTaskHandler {

	public static final String BUNDLE_KEY_LIST = "list";

	protected final String sData = "data";
	protected boolean mReload;
	protected boolean mEnableReload;
	protected ArrayList<ExtendedHashMap> mMapList;
	protected ExtendedHashMap mData;
	protected Bundle mExtras;
	protected BaseAdapter mAdapter;
	protected HttpFragmentHelper mHttpHelper;

	public AbstractHttpListFragment() {
		mHttpHelper = new HttpFragmentHelper();
	}

	protected void setDefaultLocation() {
		throw new UnsupportedOperationException("Required Method setDefaultLocation() not re-implemented");
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (mHttpHelper == null)
			mHttpHelper = new HttpFragmentHelper(this);
		else
			mHttpHelper.bindToFragment(this);
		setHasOptionsMenu(true);
		mExtras = getArguments();
		mMapList = null;

		if (savedInstanceState != null) {
			mMapList = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_LIST);
		} else {
			mMapList = new ArrayList<>();
		}

		if (mExtras != null) {
			HashMap<String, Object> map = (HashMap<String, Object>) mExtras.getSerializable("data");
			if (map != null) {
				mData = new ExtendedHashMap(map);
			}
		} else {
			mExtras = new Bundle();
		}
		DreamDroid.loadCurrentProfile(getAppCompatActivity());
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		((MainActivity)getAppCompatActivity()).unregisterFab(R.id.fab_reload);
		((MainActivity)getAppCompatActivity()).unregisterFab(R.id.fab_main);
		mHttpHelper.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mHttpHelper.onActivityCreated();
		getListView().setFastScrollEnabled(false);
		try {
			setEmptyText(getText(R.string.loading));
		} catch (IllegalStateException e) {
		}

		if (mReload)
			reload();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable(BUNDLE_KEY_LIST, mMapList);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemSelected(item.getItemId());
	}

	@Override
	public boolean hasHeader() {
		return false;
	}

	public void connectFabReload(View view, AbsListView listView) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		if (!sp.getBoolean("disable_fab_reload", false)) {
			registerFab(R.id.fab_reload, view, R.string.reload, R.drawable.ic_action_refresh, new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					reload();
				}
			}, listView, true);
			FloatingActionButton fab_reload = (FloatingActionButton) getAppCompatActivity().findViewById(R.id.fab_reload);
			fab_reload.hide();
		}
	}

	public void detachFabReload() {
		FloatingActionButton fab = (FloatingActionButton) getAppCompatActivity().findViewById(R.id.fab_reload);
		if (fab != null) {
			fab.setVisibility(View.GONE);
			((MainActivity)getAppCompatActivity()).unregisterFab(R.id.fab_reload);
		}
	}

	public void checkMenuReload(Menu menu, MenuInflater inflater) {
		if (!mEnableReload)
			return;

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		if (sp.getBoolean("disable_fab_reload", false)) {
			detachFabReload();
			inflater.inflate(R.menu.reload, menu);
		} else {
			connectFabReload(getView(), getListView());
		}
	}

	/**
	 * @param key
	 * @return
	 */
	public String getDataForKey(String key) {
		if (mData != null) {
			return (String) mData.get(key);
		}

		return null;
	}

	/**
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public String getDataForKey(String key, String defaultValue) {
		if (mData != null) {
			String str = (String) mData.get(key);
			if (str != null) {
				return str;
			}
		}
		return defaultValue;
	}

	/**
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public boolean getDataForKey(String key, boolean defaultValue) {
		if (mData != null) {
			Boolean b = (Boolean) mData.get(key);
			if (b != null) {
				return b;
			}
		}

		return defaultValue;
	}

	/**
	 * Register an <code>OnClickListener</code> for a view and a specific item
	 * ID (<code>ITEM_*</code> statics)
	 *
	 * @param v  The view an OnClickListener should be registered for
	 * @param id The id used to identify the item clicked (<code>ITEM_*</code>
	 *           statics)
	 */
	protected void registerOnClickListener(View v, final int id) {
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onItemSelected(id);
			}
		});
	}

	/**
	 * @param id
	 */
	protected boolean onItemSelected(int id) {
		Intent intent;
		switch (id) {
			case Statics.ITEM_RELOAD:
				reload();
				return true;
			case Statics.ITEM_HOME:
				intent = new Intent(getAppCompatActivity(), TabbedNavigationActivity.class);
				startActivity(intent);
				return true;
			default:
				return false;
		}
	}

	public void execSimpleResultTask(SimpleResultRequestHandler handler, ArrayList<NameValuePair> params) {
		mHttpHelper.execSimpleResultTask(handler, params);
	}

	/**
	 * @param ref The ServiceReference to zap to
	 */
	public void zapTo(String ref) {
		mHttpHelper.zapTo(ref);
	}

	/**
	 * @return
	 */
	protected String genWindowTitle(String title) {
		return title;
	}

	/**
	 * @param success
	 * @param result
	 */
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return mHttpHelper.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return mHttpHelper.onKeyUp(keyCode, event);
	}

	/**
	 * If a targetFragment has been set using setTargetFragement() return to it.
	 */
	protected void finish() {
		finish(Statics.RESULT_NONE, null);
	}

	/**
	 * If a targetFragment has been set using setTargetFragement() return to it.
	 *
	 * @param resultCode
	 */
	protected void finish(int resultCode) {
		finish(resultCode, null);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		return new ArrayList<>();
	}

	@Override
	public Bundle getLoaderBundle(int loader) {
		Bundle args = new Bundle();
		args.putSerializable("params", getHttpParams(loader));
		return args;
	}

	protected void reload(int loader) {
		mHttpHelper.reload(loader);
	}

	protected void reload() {
		reload(HttpFragmentHelper.LOADER_DEFAULT_ID);
	}

	public String getLoadFinishedTitle() {
		return getBaseTitle();
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {
		mHttpHelper.onLoadFinished();
		mMapList.clear();
		if (result.isError()) {
			mAdapter.notifyDataSetChanged();
			setEmptyText(result.getErrorText());
			return;
		}

		ArrayList<ExtendedHashMap> list = result.getResult();
		setCurrentTitle(getLoadFinishedTitle());
		getAppCompatActivity().setTitle(getCurrentTitle());

		if (list.size() == 0)
			setEmptyText(getText(R.string.no_list_item));
		else
			mMapList.addAll(list);
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onLoaderReset(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader) {
	}

	public SimpleHttpClient getHttpClient() {
		return mHttpHelper.getHttpClient();
	}

	@Override
	public void onRefresh() {
		reload();
	}

	/**
	 * @param progress
	 */
	protected void updateProgress(String progress) {
		mHttpHelper.updateProgress(progress);
	}

	@Override
	public void onProfileChanged() {
		mHttpHelper.onProfileChanged();
	}
}
