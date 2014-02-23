/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import java.util.ArrayList;
import java.util.HashMap;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.TabbedNavigationActivity;
import net.reichholf.dreamdroid.fragment.helper.DreamDroidHttpFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.HttpBaseFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;

import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;

/**
 * @author sreichholf
 * 
 */

public abstract class AbstractHttpListFragment extends DreamDroidListFragment implements
		LoaderManager.LoaderCallbacks<LoaderResult<ArrayList<ExtendedHashMap>>>, HttpBaseFragment, OnRefreshListener {
	public static final String BUNDLE_KEY_LIST = "list";

	protected final String sData = "data";
	protected boolean mReload;
	protected ArrayList<ExtendedHashMap> mMapList;
	protected ExtendedHashMap mData;
	protected Bundle mExtras;
	protected BaseAdapter mAdapter;
	protected DreamDroidHttpFragmentHelper mHttpHelper;

	public AbstractHttpListFragment() {
		mHttpHelper = new DreamDroidHttpFragmentHelper();
	}

	protected void setDefaultLocation() {
		throw new UnsupportedOperationException("Required Method setDefaultLocation() not re-implemented");
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBarActivity().setSupportProgressBarIndeterminateVisibility(false);
		if (mHttpHelper == null)
			mHttpHelper = new DreamDroidHttpFragmentHelper(this);
		else
			mHttpHelper.bindToFragment(this);
		setHasOptionsMenu(true);
		mExtras = getArguments();
		mMapList = null;

		if (savedInstanceState != null) {
			mMapList = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_LIST);
		} else {
			mMapList = new ArrayList<ExtendedHashMap>();
		}

		if (mExtras != null) {
			HashMap<String, Object> map = (HashMap<String, Object>) mExtras.getSerializable("data");
			if (map != null) {
				mData = new ExtendedHashMap(map);
			}
		} else {
			mExtras = new Bundle();
		}
		DreamDroid.loadCurrentProfile(getActionBarActivity());

	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getListView().setFastScrollEnabled(true);

		try {
			setEmptyText(getText(R.string.loading));
		} catch (IllegalStateException e) {
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		mHttpHelper.onViewCreated(view, savedInstanceState);
		if(mReload)
			reload();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable(BUNDLE_KEY_LIST, mMapList);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemClicked(item.getItemId());
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
				return b.booleanValue();
			}
		}

		return defaultValue;
	}

	/**
	 * Register an <code>OnClickListener</code> for a view and a specific item
	 * ID (<code>ITEM_*</code> statics)
	 *
	 * @param v
	 *            The view an OnClickListener should be registered for
	 * @param id
	 *            The id used to identify the item clicked (<code>ITEM_*</code>
	 *            statics)
	 */
	protected void registerOnClickListener(View v, final int id) {
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onItemClicked(id);
			}
		});
	}

	/**
	 * @param id
	 */
	protected boolean onItemClicked(int id) {
		Intent intent;
		switch (id) {
		case Statics.ITEM_HOME:
			intent = new Intent(getActionBarActivity(), TabbedNavigationActivity.class);
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
	 * @param ref
	 *            The ServiceReference to zap to
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
		mHttpHelper.onSimpleResult(success, result);
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

	protected ArrayList<NameValuePair> getHttpParams() {
		return getHttpParams(DreamDroidHttpFragmentHelper.LOADER_DEFAULT_ID);
	}

	protected ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		return params;
	}

	public Bundle getLoaderBundle() {
		return getLoaderBundle(DreamDroidHttpFragmentHelper.LOADER_DEFAULT_ID);
	}

	public Bundle getLoaderBundle(int loader) {
		Bundle args = new Bundle();
		args.putSerializable("params", getHttpParams());
		return args;
	}

	protected void reload() {
		mHttpHelper.reload();
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
			setEmptyText(result.getErrorText());
			return;
		}

		ArrayList<ExtendedHashMap> list = result.getResult();
		setCurrentTitle(getLoadFinishedTitle());
		getActionBarActivity().setTitle(getCurrentTitle());

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
    public void onRefreshStarted(View view) {
        reload();
    }

	/**
	 * @param progress
	 */
	protected void updateProgress(String progress) {
		mHttpHelper.updateProgress(progress);
	}
}
