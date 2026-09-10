package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.EventListLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerEventFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState;
import net.reichholf.dreamdroid.ui.epg.EpgListMapper;
import net.reichholf.dreamdroid.ui.epg.ServiceEpgListState;
import net.reichholf.dreamdroid.ui.epg.ServiceEpgListStateKt;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Shows the EPG of a service. Compose Material 3 list of typed {@link Event};
 * detail/timer still take ExtendedHashMap at the edge.
 *
 * @author sreichholf
 */
public class ServiceEpgListFragment extends BaseHttpRecyclerEventFragment {
	private final ArrayList<Event> mEvents = new ArrayList<>();
	private ServiceEpgListState mListState;
	private ComposeRefreshState mRefreshState;
	@Nullable
	private Job mLoadJob;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		mEnableReload = true;
		super.onCreate(savedInstanceState);
		mListState = new ServiceEpgListState();
		mRefreshState = new ComposeRefreshState();
		initTitle(getString(R.string.epg));

		mReference = getDataForKey(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE);
		mName = getDataForKey(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME);
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
		ServiceEpgListStateKt.bindServiceEpgScreen(
				compose,
				mListState,
				mRefreshState,
				() -> {
					reload();
					return kotlin.Unit.INSTANCE;
				},
				event -> {
					mCurrentItem = EpgListMapper.toExtendedHashMap(event);
					EpgDetailBottomSheet epgDetailBottomSheet = EpgDetailBottomSheet.newInstance(event);
					getMultiPaneHandler().showDialogFragment(epgDetailBottomSheet, "epg_detail_dialog");
					return kotlin.Unit.INSTANCE;
				}
		);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (mReference != null) {
			if (mEvents.isEmpty())
				reload();
		} else {
			finish();
		}
	}

	@Override
	public void onDestroyView() {
		cancelLoad(true);
		if (mEvents.isEmpty()) {
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
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("sRef", mReference));
		return params;
	}

	@Nullable
	@Override
	public String getLoadFinishedTitle() {
		return getBaseTitle() + " - " + mName;
	}

	@Override
	protected void reload() {
		if (mReference == null) {
			finish();
			return;
		}
		mReload = false;
		if (mEvents.isEmpty())
			setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
		else
			setEmptyText(null);
		loadEvents();
	}

	private void loadEvents() {
		mHttpHelper.onLoadStarted();
		if (!"".equals(getBaseTitle().trim())) {
			setCurrentTitle(getString(R.string.loading));
		}
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		cancelLoad(false);
		ArrayList<NameValuePair> params = getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID);
		mLoadJob = EventListLoadKt.launchEventListLoad(this, params, URIStore.EPG_SERVICE, (success, events, errorText) -> {
			onEventListReady(success, events, errorText);
			return Unit.INSTANCE;
		});
	}

	private void onEventListReady(boolean success, @NonNull List<Event> events, @Nullable String errorText) {
		mLoadJob = null;
		mHttpHelper.onLoadFinished();
		mEvents.clear();
		mListState.replaceAll(java.util.Collections.emptyList());
		if (!success) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}

		if (events.isEmpty()) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			mEvents.addAll(events);
			mListState.replaceAll(events);
		}
	}
}
