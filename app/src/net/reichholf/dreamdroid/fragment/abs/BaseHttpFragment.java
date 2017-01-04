/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.asynctask.SimpleResultTask;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.IHttpBase;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;


/**
 * @author sreichholf
 */

public abstract class BaseHttpFragment extends BaseFragment implements
		LoaderManager.LoaderCallbacks<LoaderResult<ExtendedHashMap>>, IHttpBase, SwipeRefreshLayout.OnRefreshListener, SimpleResultTask.SimpleResultTaskHandler {

	public static final String sData = "data";
	protected HttpFragmentHelper mHttpHelper;
	protected boolean mReload = false;

	public BaseHttpFragment() {
		mHttpHelper = new HttpFragmentHelper();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (mHttpHelper == null)
			mHttpHelper = new HttpFragmentHelper(this);
		else
			mHttpHelper.bindToFragment(this);
		setHasOptionsMenu(true);
		// CustomExceptionHandler.register(this);
		DreamDroid.loadCurrentProfile(getAppCompatActivity());
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mHttpHelper.onViewCreated(view, savedInstanceState);
		if (mReload)
			reload();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mHttpHelper.onActivityCreated();
	}

	@Override
	public void onDestroy() {
		mHttpHelper.onDestroy();
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemSelected(item.getItemId());
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
		if (v != null) {
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onItemSelected(id);
				}
			});
		}
	}

	/**
	 * @param id
	 */
	protected boolean onItemSelected(int id) {
		switch (id) {
			case Statics.ITEM_RELOAD:
				reload();
				return true;
			default:
				return false;
		}
	}

	/**
	 * @param progress
	 */
	protected void updateProgress(String progress) {
		mHttpHelper.updateProgress(progress);
	}

	/**
	 * @param event
	 */
	protected void findSimilarEvents(ExtendedHashMap event) {
		mHttpHelper.findSimilarEvents(event);
	}

	/**
	 * @param title
	 */
	protected void finishProgress(String title) {
		mHttpHelper.finishProgress(title);
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return mHttpHelper.onKeyDown(keyCode, event);
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return mHttpHelper.onKeyUp(keyCode, event);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		return new ArrayList<>();
	}

	@Override
	public Bundle getLoaderBundle(int loader) {
		Bundle args = new Bundle();
		args.putSerializable("params", getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID));
		return args;
	}

	protected void reload(int loader) {
		mHttpHelper.reload(loader);
	}

	protected void reload() {
		mHttpHelper.reload();
	}

	public String getLoadFinishedTitle() {
		return getBaseTitle();
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ExtendedHashMap>> loader, LoaderResult<ExtendedHashMap> result) {
		mHttpHelper.onLoadFinished();
		setCurrentTitle(getLoadFinishedTitle());
		getAppCompatActivity().setTitle(getCurrentTitle());
		if (result.isError()) {
			showToast(result.getErrorText());
			return;
		}
		applyData(loader.getId(), result.getResult());
	}

	@Override
	public void onLoaderReset(Loader<LoaderResult<ExtendedHashMap>> loader) {
	}

	/*
	 * You want override this if you plan to use a loader!
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.support.v4.app.LoaderManager.LoaderCallbacks#onCreateLoader(int,
	 * android.os.Bundle)
	 */
	@Override
	public Loader<LoaderResult<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		return null;
	}

	/*
	 * You want override this if you don't override onLoadFinished!
	 */
	public void applyData(int loaderId, ExtendedHashMap content) {
	}

	public void execSimpleResultTask(SimpleResultRequestHandler handler, ArrayList<NameValuePair> params) {
		mHttpHelper.execSimpleResultTask(handler, params);
	}

	public SimpleHttpClient getHttpClient() {
		return mHttpHelper.getHttpClient();
	}

	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		//mHttpHelper.onSimpleResult(success, result);
	}

	public void zapTo(String ref) {
		mHttpHelper.zapTo(ref);
	}

	@Override
	public void onRefresh() {
		reload();
	}

	@Override
	public void onProfileChanged() {
		mHttpHelper.onProfileChanged();
	}
}
