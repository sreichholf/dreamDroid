package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.BouquetListLoadKt;
import net.reichholf.dreamdroid.enigma.Bouquets;
import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.ui.pick.PickServiceListState;
import net.reichholf.dreamdroid.ui.pick.PickServiceListStateKt;
import net.reichholf.dreamdroid.ui.zap.ZapListMapper;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Bouquet / service picker. Compose Material 3 list of typed {@link Service};
 * the result Intent still carries one {@link ExtendedHashMap} under
 * {@link #KEY_BOUQUET} mapped at send time so Zap / EpgBouquet consumers stay unchanged.
 */
public class PickServiceFragment extends BaseHttpRecyclerFragment {
	public static final String KEY_BOUQUET = "bouquet";

	private final ArrayList<Service> mServices = new ArrayList<>();
	private PickServiceListState mListState;
	@Nullable
	private Job mLoadJob;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mReload = true;
		super.onCreate(savedInstanceState);
		mListState = new PickServiceListState();
		initTitle(getString(R.string.services));
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
					Intent data = new Intent();
					data.putExtra(KEY_BOUQUET, ZapListMapper.toBouquetMap(service));
					finish(Activity.RESULT_OK, data);
					return kotlin.Unit.INSTANCE;
				}
		);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
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
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		// Compose owns clicks.
	}

	@NonNull
	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		return new ArrayList<>();
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
		cancelLoad(false);
		mLoadJob = BouquetListLoadKt.launchBouquetListLoad(this, (result, bouquets, errorText) -> {
			onBouquetListReady(result, bouquets, errorText);
			return Unit.INSTANCE;
		});
	}

	private void onBouquetListReady(boolean result, Bouquets bouquets, String errorText) {
		mLoadJob = null;
		mHttpHelper.onLoadFinished();
		mServices.clear();
		mListState.replaceAll(java.util.Collections.emptyList());
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
			mListState.replaceAll(rows);
		}
	}
}
