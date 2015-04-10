package net.reichholf.dreamdroid.fragment;

import android.content.ActivityNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.PauseOnScrollListener;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.ZapListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;

/**
 * Created by reichi on 8/30/13.
 * This fragment is actually based on a GridView, it uses some small hacks to trick the ListFragment into working anyways
 * As a GridView is also using a ListAdapter, this avoids having to copy existing code
 */
public class ZapFragment extends AbstractHttpListFragment {
	public static final String BUNDLE_KEY_BOUQUETLIST = "bouquetList";
	public static String BUNDLE_KEY_CURRENT_BOUQUET = "currentBouquet";

	private GridView mGridView;
	private GetBouquetListTask mGetBouquetListTask;
	private ArrayList<ExtendedHashMap> mBouquetList;
	private ArrayAdapter<String> mBouquetListAdapter;
	private ExtendedHashMap mCurrentBouquet;
	private int mSelectedBouquetPosition;

	/**
	 * @author sreichholf Fetches a service list async. Does all the
	 *         error-handling, refreshing and title-setting
	 */
	private class GetBouquetListTask extends AsyncTask<Void, String, Boolean> {
		private ArrayList<ExtendedHashMap> mTaskList;

		@Override
		protected Boolean doInBackground(Void... unused) {
			mTaskList = new ArrayList<>();
			if (isCancelled())
				return false;

			AbstractListRequestHandler handler = new ServiceListRequestHandler();
			String ref = getResources().getStringArray(R.array.servicerefs)[0]; //Favorites TV;
			addBouquets(handler, ref);
			ref = getResources().getStringArray(R.array.servicerefs)[3]; // Favorites Radio
			addBouquets(handler, ref);

			return true;
		}

		private boolean addBouquets(AbstractListRequestHandler handler, String ref){
			ArrayList<NameValuePair> params = new ArrayList<>();
			params.add(new BasicNameValuePair("sRef", ref));
			String xml = handler.getList(getHttpClient(), params);
			if (xml != null && !isCancelled()) {
				return handler.parseList(xml, mTaskList);
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if(isCancelled())
				return;
			if (result) {
				if (getHttpClient().hasError()) {
					showToast(getString(R.string.get_content_error) + "\n" + getHttpClient().getErrorText());
				}
			}
			onBouquetListReady(result, mTaskList);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitle("");

		mBouquetList = new ArrayList<>();
		mCurrentBouquet = new ExtendedHashMap();
		mCurrentBouquet.put(Service.KEY_REFERENCE, DreamDroid.getCurrentProfile().getDefaultRef());
		mCurrentBouquet.put(Service.KEY_NAME, DreamDroid.getCurrentProfile().getDefaultRefName());

		restoreState(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.card_grid_content, container, false);

		mGridView = (GridView) view.findViewById(R.id.grid);
		mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				onListItemClick(null, view, position, id);
			}
		});
		mGridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				onListItemLongClick(null, view, position, id);
				return true;
			}
		});
		PauseOnScrollListener listener = new PauseOnScrollListener(ImageLoader.getInstance(), true, true);
		mGridView.setOnScrollListener(listener);

		restoreState(savedInstanceState);
		return view;
	}

	private void restoreState(Bundle savedInstanceState){
		boolean reload = false;
		if(savedInstanceState == null){
			mReload = true;
		} else {
			ExtendedHashMap currentBouquet = ExtendedHashMapHelper.restoreFromBundle(savedInstanceState, BUNDLE_KEY_CURRENT_BOUQUET);
			if(currentBouquet != null)
				mCurrentBouquet = currentBouquet;
			else
				mReload = true;

			ArrayList<ExtendedHashMap> bouquetList = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_BOUQUETLIST);
			if(bouquetList != null)
				mBouquetList = bouquetList;
			else
				mReload = true;
		}

		if(reload)
			mReload = true;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new ZapListAdapter(getActionBarActivity(), R.layout.zap_grid_item, mMapList);
		setListAdapter(mAdapter);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable(BUNDLE_KEY_CURRENT_BOUQUET, mCurrentBouquet);
		outState.putSerializable(BUNDLE_KEY_BOUQUETLIST, mBouquetList);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onResume() {
		setupListNavigation();
		super.onResume();
	}

	@Override
	public void onPause(){
		if(mGetBouquetListTask != null)
			mGetBouquetListTask.cancel(true);
		mGetBouquetListTask = null;
		getActionBarActivity().getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		super.onPause();
	}

	/***
	 * The ListView is fake! We do set mAdapter on the GridView.
	 *  This way all the code of "AbstractHttpListFragment" will work on a GridView
	 **/
	@Override
	public void setListAdapter(ListAdapter adapter) {
		mGridView.setAdapter(adapter);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		String ref = mMapList.get(position).getString(Service.KEY_REFERENCE);
		zapTo(ref);
	}

	public void onListItemLongClick(ListView l, View v, int position, long id) {
		String ref = mMapList.get(position).getString(Service.KEY_REFERENCE);
		String name = mMapList.get(position).getString(Service.KEY_NAME);
		try {
			startActivity(IntentFactory.getStreamServiceIntent(ref, name));
		} catch (ActivityNotFoundException e) {
			showToast(getText(R.string.missing_stream_player));
		}
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int i, Bundle bundle) {
		return new AsyncListLoader(getActionBarActivity(), new ServiceListRequestHandler(), false, bundle);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new BasicNameValuePair("sRef", mCurrentBouquet.getString(Service.KEY_REFERENCE)));

		return params;
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {

		getActionBarActivity().setSupportProgressBarIndeterminateVisibility(false);
		if(mGetBouquetListTask != null){
			mGetBouquetListTask.cancel(true);
			mGetBouquetListTask = null;
		}
		mGetBouquetListTask = new GetBouquetListTask();
		mGetBouquetListTask.execute();

		mMapList.clear();
		if (result.isError()) {
			setEmptyText(result.getErrorText());
			return;
		}

		ArrayList<ExtendedHashMap> list = result.getResult();
		setCurrentTitle(getLoadFinishedTitle());
		getActionBarActivity().setTitle(getCurrentTitle());

		if (list.size() == 0) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			for(ExtendedHashMap service : list){
				if(!Service.isMarker(service.getString(Service.KEY_REFERENCE)))
					mMapList.add(service);
			}
		}
		mAdapter.notifyDataSetChanged();
		mHttpHelper.onLoadFinished();
	}

	@Override
	public void setEmptyText(CharSequence text) {
		TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
		if (emptyView != null){
			emptyView.setText(text);
			emptyView.setVisibility(View.GONE);
		}
	}

	public void setupListNavigation() {
		ActionBar actionBar = getActionBarActivity().getSupportActionBar();
		if (actionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_LIST)
			return;
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		mBouquetListAdapter = new ArrayAdapter<>(actionBar.getThemedContext(),
				R.layout.support_simple_spinner_dropdown_item);

		actionBar.setListNavigationCallbacks(mBouquetListAdapter, new ActionBar.OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {
				mSelectedBouquetPosition = itemPosition;
				if (mBouquetList.size() > itemPosition) {
					String selectedBouquet = mBouquetListAdapter.getItem(itemPosition);
					if (mCurrentBouquet == null || !selectedBouquet.equals(mCurrentBouquet.getString(Service.KEY_NAME))) {
						mCurrentBouquet = mBouquetList.get(itemPosition);
						reload();
					}
				}
				return true;
			}
		});
		ArrayList<ExtendedHashMap> list = new ArrayList<>(mBouquetList);
		applyBouquetList(list);
		mBouquetListAdapter.notifyDataSetChanged();
	}

	public void onBouquetListReady(boolean result, ArrayList<ExtendedHashMap> list){
		applyBouquetList(list);
	}

	private void applyBouquetList(ArrayList<ExtendedHashMap> list){
		mBouquetList.clear();
		mBouquetListAdapter.clear();

		String defaultRef = DreamDroid.getCurrentProfile().getDefaultRef();
		boolean isDefaultMissing = true;

		int position = mSelectedBouquetPosition = 0;
		for( ExtendedHashMap service : list){
			mBouquetList.add(service);
			mBouquetListAdapter.add(service.getString(Service.KEY_NAME));
			if(defaultRef != null && !"".equals(defaultRef) && service.getString(Service.KEY_REFERENCE).equals(defaultRef))
				isDefaultMissing = false;
			if(service.getString(Service.KEY_REFERENCE).equals(mCurrentBouquet.getString(Service.KEY_REFERENCE)))
				mSelectedBouquetPosition = position;
			position++;
		}
		if(isDefaultMissing){
			addDefaultBouquetToList();
		}
		getActionBarActivity().getSupportActionBar().setSelectedNavigationItem(mSelectedBouquetPosition);
		mBouquetListAdapter.notifyDataSetChanged();
	}

	private void addDefaultBouquetToList(){
		ExtendedHashMap defaultBouquet = new ExtendedHashMap();
		String defaultRef = DreamDroid.getCurrentProfile().getDefaultRef();
		if("".equals(defaultRef))
			return;
		defaultBouquet.put(Service.KEY_REFERENCE, defaultRef);
		defaultBouquet.put(Service.KEY_NAME, DreamDroid.getCurrentProfile().getDefaultRefName());
		mBouquetList.add(0, defaultBouquet);
		mBouquetListAdapter.insert(defaultBouquet.getString(Service.KEY_NAME), 0);
	}
}
