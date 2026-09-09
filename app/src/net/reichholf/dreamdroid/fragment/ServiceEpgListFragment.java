package net.reichholf.dreamdroid.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.asynctask.GetEventListTask;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerEventFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.ui.epg.EpgListMapper;
import net.reichholf.dreamdroid.ui.epg.ServiceEpgListState;
import net.reichholf.dreamdroid.ui.epg.ServiceEpgListStateKt;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the EPG of a service. Compose Material 3 list of typed {@link Event};
 * detail/timer still take ExtendedHashMap at the edge.
 *
 * @author sreichholf
 */
public class ServiceEpgListFragment extends BaseHttpRecyclerEventFragment
		implements GetEventListTask.GetEventListTaskHandler {
	private final ArrayList<Event> mEvents = new ArrayList<>();
	private ServiceEpgListState mListState;
	@Nullable
	private GetEventListTask mEventListTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		mEnableReload = true;
		super.onCreate(savedInstanceState);
		mListState = new ServiceEpgListState();
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
		ServiceEpgListStateKt.bindServiceEpgScreen(
				compose,
				mListState,
				event -> {
					mCurrentItem = EpgListMapper.toExtendedHashMap(event);
					EpgDetailBottomSheet epgDetailBottomSheet = EpgDetailBottomSheet.newInstance(mCurrentItem);
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
	public void onDestroy() {
		if (mEventListTask != null) {
			mEventListTask.cancel(true);
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
		params.add(new NameValuePair("sRef", mReference));
		return params;
	}

	@Nullable
	@Override
	public String getLoadFinishedTitle() {
		return getBaseTitle() + " - " + mName;
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		// Service EPG no longer starts this loader. BaseHttpRecyclerFragment still requires LoaderCallbacks.
		return new AsyncListLoader(getAppCompatActivity(), new EventListRequestHandler(), false, args);
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   @NonNull LoaderResult<ArrayList<ExtendedHashMap>> result) {
		// Unused: rows come from GetEventListTask / EnigmaClient.
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
		if (mEventListTask != null) {
			mEventListTask.cancel(true);
		}
		mEventListTask = new GetEventListTask(this);
		mEventListTask.execute(getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID));
	}

	@Override
	public void onEventListReady(boolean success, @NonNull List<Event> events, @Nullable String errorText) {
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
