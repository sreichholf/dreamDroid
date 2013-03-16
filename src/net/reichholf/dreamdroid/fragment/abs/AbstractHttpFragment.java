/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment.abs;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.fragment.helper.DreamDroidHttpFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.HttpBaseFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;

import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * @author sreichholf
 * 
 */
public abstract class AbstractHttpFragment extends DreamDroidFragment implements
		LoaderManager.LoaderCallbacks<LoaderResult<ExtendedHashMap>>, HttpBaseFragment {

	protected final String sData = "data";
	protected DreamDroidHttpFragmentHelper mHttpHelper;

	public AbstractHttpFragment() {
		mHttpHelper = new DreamDroidHttpFragmentHelper();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (mHttpHelper == null)
			mHttpHelper = new DreamDroidHttpFragmentHelper(this);
		else
			mHttpHelper.bindToFragment(this);
		setHasOptionsMenu(true);
		// CustomExceptionHandler.register(this);
		DreamDroid.loadCurrentProfile(getSherlockActivity());
	}

	@Override
	public void onDestroy() {
		mHttpHelper.onDestroy();
		super.onDestroy();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemClicked(item.getItemId());
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
		if (v != null) {
			v.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					onItemClicked(id);
				}
			});
		}
	}

	/**
	 * @param id
	 */
	protected boolean onItemClicked(int id) {
		return false;
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

	/**
	 * @param toastText
	 */
	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getSherlockActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * @param toastText
	 */
	protected void showToast(CharSequence toastText) {
		mHttpHelper.showToast(toastText);
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return mHttpHelper.onKeyDown(keyCode, event);
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return mHttpHelper.onKeyUp(keyCode, event);
	}

	protected ArrayList<NameValuePair> getHttpParams() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		return params;
	}

	public Bundle getLoaderBundle() {
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
	public void onLoadFinished(Loader<LoaderResult<ExtendedHashMap>> loader, LoaderResult<ExtendedHashMap> result) {
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
		setCurrentTitle(getLoadFinishedTitle());
		getSherlockActivity().setTitle(getCurrentTitle());
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
		mHttpHelper.onSimpleResult(success, result);
	}

	public void zapTo(String ref) {
		mHttpHelper.zapTo(ref);
	}
}
