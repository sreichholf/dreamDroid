package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceNameAdapter;
import net.reichholf.dreamdroid.asynctask.GetBouquetListTask;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.ui.zap.ZapListMapper;
import net.reichholf.dreamdroid.view.recyclerview.DividerItemDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Bouquet / service picker. Rows are typed {@link Service}; the result Intent still
 * carries one {@link ExtendedHashMap} under {@link #KEY_BOUQUET} mapped at send time
 * so Zap / EpgBouquet consumers stay unchanged.
 */
public class PickServiceFragment extends BaseHttpRecyclerFragment
		implements GetBouquetListTask.GetBouquetListTaskHandler {
	public static final String KEY_BOUQUET = "bouquet";

	private final ArrayList<Service> mServices = new ArrayList<>();
	@Nullable
	private GetBouquetListTask mBouquetListTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mReload = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.services));
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		mAdapter = new ServiceNameAdapter(mServices, android.R.layout.simple_list_item_1);
		getRecyclerView().setAdapter(mAdapter);
		getRecyclerView().addItemDecoration(new DividerItemDecoration(getAppCompatActivity(), null));
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onDestroy() {
		if (mBouquetListTask != null) {
			mBouquetListTask.cancel(true);
		}
		super.onDestroy();
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		Service selected = mServices.get(position);
		Intent data = new Intent();
		data.putExtra(KEY_BOUQUET, ZapListMapper.toBouquetMap(selected));
		finish(Activity.RESULT_OK, data);
	}

	@NonNull
	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		return new ArrayList<>();
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int i, Bundle args) {
		// Picker no longer starts this loader. BaseHttpRecyclerFragment still requires LoaderCallbacks.
		return new AsyncListLoader(getAppCompatActivity(), new ServiceListRequestHandler(), false, args);
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   @NonNull LoaderResult<ArrayList<ExtendedHashMap>> result) {
		// Unused: rows come from GetBouquetListTask / EnigmaClient.
	}

	@Override
	protected void reload() {
		mReload = false;
		if (mServices.isEmpty())
			setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
		else
			setEmptyText(null);
		loadBouquets();
	}

	private void loadBouquets() {
		mHttpHelper.onLoadStarted();
		if (!"".equals(getBaseTitle().trim())) {
			setCurrentTitle(getString(R.string.loading));
		}
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (mBouquetListTask != null) {
			mBouquetListTask.cancel(true);
		}
		mBouquetListTask = new GetBouquetListTask(this);
		mBouquetListTask.execute();
	}

	@Override
	public void onBouquetListReady(boolean result, GetBouquetListTask.Bouquets bouquets, String errorText) {
		mHttpHelper.onLoadFinished();
		mServices.clear();
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
		if (!result) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}

		List<Service> rows = new ArrayList<>();
		if (bouquets != null) {
			if (bouquets.tv != null) {
				rows.addAll(bouquets.tv);
			}
			if (bouquets.radio != null) {
				rows.addAll(bouquets.radio);
			}
		}
		if (rows.isEmpty()) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			mServices.addAll(rows);
		}
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
	}
}
