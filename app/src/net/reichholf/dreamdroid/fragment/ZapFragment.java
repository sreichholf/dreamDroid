package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.enigma.ServiceListLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState;
import net.reichholf.dreamdroid.ui.zap.ZapListMapper;
import net.reichholf.dreamdroid.ui.zap.ZapListState;
import net.reichholf.dreamdroid.ui.zap.ZapListStateKt;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;


/**
 * Zap channel grid. Compose Material 3 grid; zap HTTP stays in HttpFragmentHelper.
 */

public class ZapFragment extends BaseHttpRecyclerFragment {
	@NonNull
	public static String BUNDLE_KEY_CURRENT_BOUQUET_REFERENCE = "currentBouquetReference";
	@NonNull
	public static String BUNDLE_KEY_CURRENT_BOUQUET_NAME = "currentBouquetName";

	private Service mCurrentBouquet;
	private final ArrayList<Service> mServices = new ArrayList<>();
	private ZapListState mListState;
	private ComposeRefreshState mRefreshState;
	private boolean mWaitingForPicker;
	@Nullable
	private Job mLoadJob;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mEnableReload = false;
		super.onCreate(savedInstanceState);
		mListState = new ZapListState();
		mRefreshState = new ComposeRefreshState();
		if (mCurrentBouquet == null) {
			mReload = true;
			initTitle("");
			if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_KEY_CURRENT_BOUQUET_REFERENCE)) {
				mCurrentBouquet = new Service(
						savedInstanceState.getString(BUNDLE_KEY_CURRENT_BOUQUET_REFERENCE, ""),
						savedInstanceState.getString(BUNDLE_KEY_CURRENT_BOUQUET_NAME, "")
				);
			} else {
				String ref = DreamDroid.getCurrentProfile().getDefaultBouquetTv();
				String name = DreamDroid.getCurrentProfile().getDefaultBouquetTvName();
				mCurrentBouquet = new Service(ref != null ? ref : "", name != null ? name : "");
			}
			mWaitingForPicker = false;
		}
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
		mHttpHelper.setComposeRefresh(mRefreshState);
		ZapListStateKt.bindZapScreen(
				compose,
				mListState,
				mRefreshState,
				() -> {
					reload();
					return kotlin.Unit.INSTANCE;
				},
				service -> {
					zapTo(service.getReference());
					return kotlin.Unit.INSTANCE;
				},
				service -> {
					try {
						startActivity(IntentFactory.getStreamServiceIntent(
								getAppCompatActivity(),
								service.getReference(),
								service.getName()));
					} catch (ActivityNotFoundException e) {
						showToast(getText(R.string.missing_stream_player));
					}
					return kotlin.Unit.INSTANCE;
				}
		);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (mCurrentBouquet != null) {
			outState.putString(BUNDLE_KEY_CURRENT_BOUQUET_REFERENCE, mCurrentBouquet.getReference());
			outState.putString(BUNDLE_KEY_CURRENT_BOUQUET_NAME, mCurrentBouquet.getName());
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroyView() {
		cancelLoad(true);
		if (mServices.isEmpty()) {
			mReload = true;
		}
		super.onDestroyView();
	}

	private void cancelLoad(boolean finishUi) {
		if (mLoadJob == null) {
			return;
		}
		mLoadJob.cancel(null);
		mLoadJob = null;
		if (finishUi) {
			mHttpHelper.onLoadFinished();
		}
	}

	@Override
	public void createOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.epgbouquet, menu);
	}


	@Override
	public void onItemClick(RecyclerView rv, View v, int position, long id) {
		// Clicks come from Compose; hidden RecyclerView is unused.
	}

	@Override
	public boolean onItemLongClick(RecyclerView rv, View v, int position, long id) {
		return false;
	}

	@NonNull
	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		String ref = mCurrentBouquet != null ? mCurrentBouquet.getReference() : "";
		params.add(new NameValuePair("sRef", ref));

		return params;
	}

	@Override
	protected void reload() {
		if (mCurrentBouquet != null) {
			mReload = false;
			if (mServices.isEmpty())
				setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
			else
				setEmptyText(null);
			loadServices();
		} else if (!mWaitingForPicker)
			pickBouquet();
	}

	private void loadServices() {
		mHttpHelper.onLoadStarted();
		if (!"".equals(getBaseTitle().trim())) {
			setCurrentTitle(getString(R.string.loading));
		}
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		cancelLoad(false);
		ArrayList<NameValuePair> params = getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID);
		mLoadJob = ServiceListLoadKt.launchServiceListLoad(this, params, (success, services, errorText) -> {
			onServiceListReady(success, services, errorText);
			return Unit.INSTANCE;
		});
	}

	private void onServiceListReady(boolean success, @NonNull List<Service> services, @Nullable String errorText) {
		mLoadJob = null;
		mHttpHelper.onLoadFinished();
		mServices.clear();
		mListState.replaceAll(java.util.Collections.emptyList());
		if (!success) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		List<Service> rows = ZapListMapper.rowsFrom(services);
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}

		if (rows.isEmpty()) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			mServices.addAll(rows);
			mListState.replaceAll(rows);
		}
	}

	@Nullable
	@Override
	public String getLoadFinishedTitle() {
		if (mCurrentBouquet != null && mCurrentBouquet.getName() != null && !mCurrentBouquet.getName().isEmpty())
			return mCurrentBouquet.getName();
		return super.getLoadFinishedTitle();
	}

	@Override
	protected boolean onItemSelected(int id) {
		switch (id) {
			case R.id.menu_pick_bouquet:
				pickBouquet();
				return true;
		}
		return super.onItemSelected(id);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
		if (resultCode != Activity.RESULT_OK)
			return;
		switch (requestCode) {
			case Statics.REQUEST_PICK_BOUQUET:
				ExtendedHashMap bouquetMap = (ExtendedHashMap) data.getSerializableExtra(PickServiceFragment.KEY_BOUQUET);
				Service bouquet = ZapListMapper.bouquetFrom(bouquetMap);
				if (!bouquet.getReference().equals(mCurrentBouquet.getReference())) {
					mCurrentBouquet = bouquet;
					mListState.scrollToTop();
				}
				reload();
				mWaitingForPicker = false;
				break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void pickBouquet() {
		mWaitingForPicker = true;
		PickServiceFragment f = new PickServiceFragment();
		Bundle args = new Bundle();

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE, "default");

		args.putSerializable(sData, data);
		args.putString("action", Statics.INTENT_ACTION_PICK_BOUQUET);

		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_PICK_BOUQUET);
		((MultiPaneHandler) getAppCompatActivity()).showDetails(f, true);
	}
}
