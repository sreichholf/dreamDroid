package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.asynctask.GetBouquetListTask;
import net.reichholf.dreamdroid.asynctask.GetServiceListTask;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.ui.pick.PickServiceListState;
import net.reichholf.dreamdroid.ui.pick.PickServiceListStateKt;
import net.reichholf.dreamdroid.ui.zap.ZapListMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Timer service picker. Compose Material 3: bouquet list then channel list via typed
 * {@link Service}. Result Intent still carries one {@link ExtendedHashMap} under
 * {@link #sData} so {@link TimerEditFragment} stays unchanged at the edge.
 */
public class TimerServicePickFragment extends BaseHttpRecyclerFragment
		implements GetBouquetListTask.GetBouquetListTaskHandler,
		GetServiceListTask.GetServiceListTaskHandler {

	private final ArrayList<Service> mBouquets = new ArrayList<>();
	private final ArrayList<Service> mServices = new ArrayList<>();
	private PickServiceListState mListState;
	@Nullable
	private Service mCurrentBouquet;
	@Nullable
	private GetBouquetListTask mBouquetListTask;
	@Nullable
	private GetServiceListTask mServiceListTask;
	/** Bumped when starting a load so late AsyncTask callbacks are ignored. */
	private int mLoadGeneration;
	private int mBouquetReadyForGeneration;
	private int mServiceReadyForGeneration;
	private OnBackPressedCallback mBackCallback;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mReload = true;
		super.onCreate(savedInstanceState);
		mListState = new PickServiceListState();
		initTitle(getString(R.string.service));
		if (savedInstanceState != null) {
			String ref = savedInstanceState.getString("bouquetRef");
			String name = savedInstanceState.getString("bouquetName");
			if (ref != null && !ref.isEmpty()) {
				mCurrentBouquet = new Service(ref, name != null ? name : "");
			}
		}
		mBackCallback = new OnBackPressedCallback(mCurrentBouquet != null) {
			@Override
			public void handleOnBackPressed() {
				showBouquetList();
			}
		};
		requireActivity().getOnBackPressedDispatcher().addCallback(this, mBackCallback);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.compose_swipe_list, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		ComposeView compose = view.findViewById(R.id.compose_list);
		PickServiceListStateKt.bindPickServiceScreen(
				compose,
				mListState,
				service -> {
					onRowClick(service);
					return kotlin.Unit.INSTANCE;
				}
		);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (mCurrentBouquet != null) {
			outState.putString("bouquetRef", mCurrentBouquet.getReference());
			outState.putString("bouquetName", mCurrentBouquet.getName());
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroy() {
		if (mBouquetListTask != null) {
			mBouquetListTask.cancel(true);
		}
		if (mServiceListTask != null) {
			mServiceListTask.cancel(true);
		}
		super.onDestroy();
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		// Compose owns clicks.
	}

	@NonNull
	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		if (mCurrentBouquet != null) {
			params.add(new NameValuePair("sRef", mCurrentBouquet.getReference()));
		}
		return params;
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int i, Bundle args) {
		return new AsyncListLoader(getAppCompatActivity(), new ServiceListRequestHandler(), false, args);
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   @NonNull LoaderResult<ArrayList<ExtendedHashMap>> result) {
		// Unused: rows come from GetBouquetListTask / GetServiceListTask.
	}

	@Override
	protected void reload() {
		mReload = false;
		if (mCurrentBouquet == null) {
			if (mBouquets.isEmpty()) {
				setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
			} else {
				setEmptyText(null);
			}
			loadBouquets();
		} else {
			if (mServices.isEmpty()) {
				setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
			} else {
				setEmptyText(null);
			}
			loadServices();
		}
	}

	private void showBouquetList() {
		mCurrentBouquet = null;
		mServices.clear();
		mLoadGeneration++;
		if (mServiceListTask != null) {
			mServiceListTask.cancel(true);
			mServiceListTask = null;
		}
		mBackCallback.setEnabled(false);
		if (!mBouquets.isEmpty()) {
			setEmptyText(null);
			mListState.replaceAll(mBouquets);
			setCurrentTitle(getString(R.string.service));
			if (getAppCompatActivity() != null) {
				getAppCompatActivity().setTitle(getCurrentTitle());
			}
		} else {
			mListState.replaceAll(Collections.emptyList());
			loadBouquets();
		}
	}

	private void onRowClick(@NonNull Service service) {
		if (mCurrentBouquet == null) {
			if (net.reichholf.dreamdroid.helpers.enigma2.Service.isMarker(service.getReference())) {
				return;
			}
			mCurrentBouquet = service;
			mServices.clear();
			mListState.replaceAll(Collections.emptyList());
			setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
			mBackCallback.setEnabled(true);
			loadServices();
			return;
		}
		if (net.reichholf.dreamdroid.helpers.enigma2.Service.isMarker(service.getReference())) {
			return;
		}
		Intent data = new Intent();
		data.putExtra(sData, ZapListMapper.toBouquetMap(service));
		finish(Activity.RESULT_OK, data);
	}

	private void loadBouquets() {
		mHttpHelper.onLoadStarted();
		setCurrentTitle(getString(R.string.loading));
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (mServiceListTask != null) {
			mServiceListTask.cancel(true);
			mServiceListTask = null;
		}
		if (mBouquetListTask != null) {
			mBouquetListTask.cancel(true);
		}
		mLoadGeneration++;
		mBouquetReadyForGeneration = mLoadGeneration;
		mBouquetListTask = new GetBouquetListTask(this);
		mBouquetListTask.execute();
	}

	private void loadServices() {
		mHttpHelper.onLoadStarted();
		String title = mCurrentBouquet != null ? mCurrentBouquet.getName() : getString(R.string.service);
		setCurrentTitle(getString(R.string.loading));
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		if (mBouquetListTask != null) {
			mBouquetListTask.cancel(true);
			mBouquetListTask = null;
		}
		if (mServiceListTask != null) {
			mServiceListTask.cancel(true);
		}
		mLoadGeneration++;
		mServiceReadyForGeneration = mLoadGeneration;
		setBaseTitle(title);
		mServiceListTask = new GetServiceListTask(this);
		mServiceListTask.execute(getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID));
	}

	@Override
	public void onBouquetListReady(boolean result, GetBouquetListTask.Bouquets bouquets, String errorText) {
		if (mBouquetReadyForGeneration != mLoadGeneration || mCurrentBouquet != null) {
			return;
		}
		mHttpHelper.onLoadFinished();
		mBouquets.clear();
		mListState.replaceAll(Collections.emptyList());
		if (!result) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		setCurrentTitle(getString(R.string.service));
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
			mBouquets.addAll(rows);
			mListState.replaceAll(rows);
		}
	}

	@Override
	public void onServiceListReady(boolean success, @NonNull List<Service> services, @Nullable String errorText) {
		if (mServiceReadyForGeneration != mLoadGeneration || mCurrentBouquet == null) {
			return;
		}
		mHttpHelper.onLoadFinished();
		mServices.clear();
		mListState.replaceAll(Collections.emptyList());
		if (!success) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		String title = mCurrentBouquet.getName();
		setCurrentTitle(title);
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(title);
		}
		List<Service> rows = ZapListMapper.rowsFrom(services);
		if (rows.isEmpty()) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			mServices.addAll(rows);
			mListState.replaceAll(rows);
		}
	}
}
