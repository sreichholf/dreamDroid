package net.reichholf.dreamdroid.fragment.abs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.TabbedNavigationActivity;
import net.reichholf.dreamdroid.asynctask.SimpleResultTask;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.IHttpBase;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Stephan on 03.05.2015.
 */
public abstract class BaseHttpRecyclerFragment extends BaseRecyclerFragment implements
		LoaderManager.LoaderCallbacks<LoaderResult<ArrayList<ExtendedHashMap>>>, IHttpBase, SimpleResultTask.SimpleResultTaskHandler, ActionDialog.DialogActionListener {

	protected final String sData = "data";

	protected boolean mReload;
	protected ArrayList<ExtendedHashMap> mMapList;
	protected ExtendedHashMap mData;
	protected Bundle mExtras;
	protected RecyclerView.Adapter mAdapter;
	protected HttpFragmentHelper mHttpHelper;

	public BaseHttpRecyclerFragment() {
		mHttpHelper = new HttpFragmentHelper();
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
		mMapList = new ArrayList<>();

		if (mExtras != null) {
			ExtendedHashMap map = (ExtendedHashMap) mExtras.getSerializable("data");
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
		mHttpHelper.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mHttpHelper.onActivityCreated();

		try {
			setEmptyText(null);
		} catch (IllegalStateException e) {
		}

		if (mReload)
			reload();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemSelected(item.getItemId());
	}

	public void connectFabReload() {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		if (sp.getBoolean("disable_fab_reload", false))
			return;
		registerFab(R.id.fab_reload, R.string.reload, R.drawable.ic_action_refresh, v -> reload(), true);
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		checkMenuReload(menu, inflater);
	}

	public void detachFabReload() {
		FloatingActionButton fab = getAppCompatActivity().findViewById(R.id.fab_reload);
		if (fab != null) {
			setFabEnabled(fab.getId(), false);
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
			connectFabReload();
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
		v.setOnClickListener(v1 -> onItemSelected(id));
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

	@Override
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
		mReload = false;
		if(mMapList.isEmpty())
			setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
		else
			setEmptyText(null);
		reload(HttpFragmentHelper.LOADER_DEFAULT_ID);
	}

	public String getLoadFinishedTitle() {
		return getBaseTitle();
	}

	@Override
	public void onLoadFinished(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {
		mHttpHelper.onLoadFinished();
		mMapList.clear();
		if (result.isError()) {
			mAdapter.notifyDataSetChanged();
			setEmptyText(result.getErrorText());
			return;
		}
		setEmptyText(null);
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
	public void onLoaderReset(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader) {
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
