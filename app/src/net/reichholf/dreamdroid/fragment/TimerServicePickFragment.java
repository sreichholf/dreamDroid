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
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.BouquetListLoadKt;
import net.reichholf.dreamdroid.enigma.Bouquets;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.enigma.ServiceListLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.ui.pick.PickServiceListState;
import net.reichholf.dreamdroid.ui.pick.PickServiceListStateKt;
import net.reichholf.dreamdroid.ui.zap.ZapListMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Timer service picker. Compose Material 3: bouquet list then channel list via typed
 * {@link Service}. Result Intent still carries one {@link ExtendedHashMap} under
 * {@link #sData} so {@link TimerEditFragment} stays unchanged at the edge.
 */
public class TimerServicePickFragment extends BaseHttpRecyclerFragment {

	private final ArrayList<Service> mBouquets = new ArrayList<>();
	private final ArrayList<Service> mServices = new ArrayList<>();
	private PickServiceListState mListState;
	@Nullable
	private Service mCurrentBouquet;
	@Nullable
	private Job mBouquetLoadJob;
	@Nullable
	private Job mServiceLoadJob;
	/** Bumped when starting a load so late callbacks are ignored. */
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
	public void onDestroyView() {
		cancelBouquetLoad(true);
		cancelServiceLoad(true);
		if (mBouquets.isEmpty() && mServices.isEmpty()) {
			mReload = true;
		}
		super.onDestroyView();
	}

	private void cancelBouquetLoad(boolean finishUi) {
		if (mBouquetLoadJob == null) {
			return;
		}
		mBouquetLoadJob.cancel(null);
		mBouquetLoadJob = null;
		if (finishUi) {
			mHttpHelper.onLoadFinished();
		}
	}

	private void cancelServiceLoad(boolean finishUi) {
		if (mServiceLoadJob == null) {
			return;
		}
		mServiceLoadJob.cancel(null);
		mServiceLoadJob = null;
		if (finishUi) {
			mHttpHelper.onLoadFinished();
		}
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
		cancelServiceLoad(false);
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
		cancelServiceLoad(false);
		cancelBouquetLoad(false);
		mLoadGeneration++;
		mBouquetReadyForGeneration = mLoadGeneration;
		final int generation = mBouquetReadyForGeneration;
		mBouquetLoadJob = BouquetListLoadKt.launchBouquetListLoad(this, (result, bouquets, errorText) -> {
			onBouquetListReady(generation, result, bouquets, errorText);
			return Unit.INSTANCE;
		});
	}

	private void loadServices() {
		mHttpHelper.onLoadStarted();
		String title = mCurrentBouquet != null ? mCurrentBouquet.getName() : getString(R.string.service);
		setCurrentTitle(getString(R.string.loading));
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		cancelBouquetLoad(false);
		cancelServiceLoad(false);
		mLoadGeneration++;
		mServiceReadyForGeneration = mLoadGeneration;
		setBaseTitle(title);
		ArrayList<NameValuePair> params = getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID);
		final int generation = mServiceReadyForGeneration;
		mServiceLoadJob = ServiceListLoadKt.launchServiceListLoad(this, params, (success, services, errorText) -> {
			onServiceListReady(generation, success, services, errorText);
			return Unit.INSTANCE;
		});
	}

	private void onBouquetListReady(int generation, boolean result, Bouquets bouquets, String errorText) {
		mBouquetLoadJob = null;
		if (generation != mLoadGeneration || mCurrentBouquet != null) {
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

	private void onServiceListReady(int generation, boolean success, @NonNull List<Service> services,
			@Nullable String errorText) {
		mServiceLoadJob = null;
		if (generation != mLoadGeneration || mCurrentBouquet == null) {
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
