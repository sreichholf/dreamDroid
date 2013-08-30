package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.PauseOnScrollListener;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.ZapListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
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
public class ZapFragment extends AbstractHttpListFragment{
	private GridView mGridView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		if (savedInstanceState == null) {
			reload();
		}
		initTitle(getString(R.string.zap));
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.card_grid_view, container, false);

		mGridView = (GridView) view.findViewById(R.id.grid);
		mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				onListItemClick(null, view, position, id);
			}
		});

		PauseOnScrollListener listener = new PauseOnScrollListener(ImageLoader.getInstance(), true, true);
		mGridView.setOnScrollListener(listener);

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new ZapListAdapter(getActionBarActivity(), R.layout.zap_grid_item, mMapList);
		setListAdapter(mAdapter);
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

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int i, Bundle bundle) {
		return new AsyncListLoader(getActionBarActivity(), new ServiceListRequestHandler(), false, bundle);
	}

	@Override
	protected ArrayList<NameValuePair> getHttpParams() {
		String ref = DreamDroid.getCurrentProfile().getDefaultRef();
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", ref));

		return params;
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {
		getActionBarActivity().setProgressBarIndeterminateVisibility(false);
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
		else {
			for(ExtendedHashMap service : list){
				if(!Service.isMarker(service.getString(Service.KEY_REFERENCE)))
					mMapList.add(service);
			}
		}
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void setEmptyText(CharSequence text) {
		TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
		if (emptyView != null){
			emptyView.setText(text);
			emptyView.setVisibility(View.GONE);
		}
	}
}
