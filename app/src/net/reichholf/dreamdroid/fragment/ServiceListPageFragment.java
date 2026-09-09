/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.compose.ui.platform.ComposeView;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter;
import net.reichholf.dreamdroid.asynctask.GetEpgNowNextTask;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.ServiceNowNext;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerEventFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.room.AppDatabase;
import net.reichholf.dreamdroid.ui.epg.EpgListMapper;
import net.reichholf.dreamdroid.ui.services.ServiceListItem;
import net.reichholf.dreamdroid.ui.services.ServiceListMapperKt;
import net.reichholf.dreamdroid.ui.services.ServiceListState;
import net.reichholf.dreamdroid.ui.services.ServiceListStateKt;

import java.util.ArrayList;
import java.util.List;


/**
 * Handles ServiceLists of (based on service references).
 * <p/>
 * Compose Material 3 list of typed {@link ServiceNowNext} for TV/Radio hub pages.
 * Detail/timer/stream still take ExtendedHashMap at the edge.
 *
 * @author sreichholf
 */
public class ServiceListPageFragment extends BaseHttpRecyclerEventFragment
		implements GetEpgNowNextTask.GetEpgNowNextTaskHandler {
	@Nullable
	@State
	public String mName;
	@Nullable
	@State
	public String mRef;

	private ArrayList<ExtendedHashMap> mHistory;
	private final ArrayList<ServiceNowNext> mRows = new ArrayList<>();
	private ServiceListState mListState;
	@Nullable
	private GetEpgNowNextTask mEpgNowNextTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mName = getString(R.string.services);
		mHasFabMain = false;
		mEnableReload = false;

		Bundle args = getArguments();
		if (args != null)  {
			mRef = args.getString(Service.KEY_REFERENCE, null);
			mName = args.getString(Service.KEY_NAME, "-");
		}

		mCurrentItem = new ExtendedHashMap();
		mCurrentItem.put(Service.KEY_REFERENCE, mRef);
		mCurrentItem.put(Service.KEY_NAME, mName);
		mHistory = new ArrayList<>();

		if (mRef == null) {
			mRef = DreamDroid.getCurrentProfile().getDefaultBouquetTv();
			mName = DreamDroid.getCurrentProfile().getDefaultBouquetTvName();
		}
		mListState = new ServiceListState();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		mAdapter = new ServiceAdapter(getAppCompatActivity(), mMapList);
		getRecyclerView().setAdapter(mAdapter);

		super.onActivityCreated(savedInstanceState);
		getAppCompatActivity().supportInvalidateOptionsMenu();
		getAppCompatActivity().setTitle(mName);
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
		ServiceListStateKt.bindServiceListScreen(
				compose,
				mListState,
				item -> {
					onComposeClick(item, false);
					return kotlin.Unit.INSTANCE;
				},
				item -> {
					onComposeClick(item, true);
					return kotlin.Unit.INSTANCE;
				}
		);
	}

	@Override
	public void onResume() {
		super.onResume();
		reload();
	}

	@Override
	public void onDestroy() {
		if (mEpgNowNextTask != null) {
			mEpgNowNextTask.cancel(true);
		}
		super.onDestroy();
	}

	@Override
	public void onItemClick(RecyclerView parent, @NonNull View view, int position, long id) {
		// Compose owns clicks.
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, @NonNull View view, int position, long id) {
		return true;
	}

	private void onComposeClick(@NonNull ServiceListItem item, boolean isLong) {
		View host = getView();
		if (host == null) {
			return;
		}
		onItemClick(host, item.getIndex(), isLong);
	}

	private void onItemClick(@NonNull View v, int position, boolean isLong) {
		if (position < 0 || position >= mRows.size()) {
			return;
		}
		ServiceNowNext row = mRows.get(position);
		ExtendedHashMap previousItem = mCurrentItem;
		mCurrentItem = ServiceListMapperKt.serviceNowNextToExtendedHashMap(row);
		final String ref = row.getServiceReference();
		final String name = row.getServiceName();
		if (Service.isMarker(ref))
			return;

		if (Service.isDirectory(ref)) {
			mHistory.add(previousItem);
			mRef = ref;
			mName = name;
			reload();
			return;
		}

		boolean instantZap = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity()).getBoolean(
				"instant_zap", false);
		if ((instantZap && !isLong) || (!instantZap && isLong)) {
			zapTo(ref);
		} else {
			showPopupMenu(v, row);
		}
	}

	@Override
	public void createOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.servicelistpage, menu);
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		if (getMultiPaneHandler().isDrawerOpen())
			return;

		MenuItem setDefault = menu.findItem(R.id.menu_default);
		String defaultReference = DreamDroid.getCurrentProfile().getDefaultBouquetTv();
		setDefault.setVisible(true);
		if (defaultReference != null) {
			if (defaultReference.equals(mRef)) {
				setDefault.setIcon(R.drawable.ic_action_fav);
				setDefault.setTitle(R.string.reset_default);
			} else {
				setDefault.setIcon(R.drawable.ic_action_nofav);
				setDefault.setTitle(R.string.set_default);
			}
		}
	}

	@Override
	protected boolean onItemSelected(int id) {
		switch (id) {
			case Statics.ITEM_SET_DEFAULT:
				if (mRef != null) {
					Profile p = DreamDroid.getCurrentProfile();
					boolean reset = false;
					if (p.getDefaultBouquetTv() != null && p.getDefaultBouquetTv().equals(mRef)) {
						p.setDefaultBouquetTv(null);
						reset = true;
					} else {
						p.setDefaultRefValues(mRef, mName);
					}

					Profile.ProfileDao dao = AppDatabase.profiles(getAppCompatActivity());
					dao.updateProfile(p);
					if (!reset)
						showToast(getText(R.string.default_bouquet_set_to) + " '" + mName + "'");
				} else {
					showToast(getText(R.string.default_bouquet_not_set));
				}
				getAppCompatActivity().supportInvalidateOptionsMenu();
				return true;
			default:
				return super.onItemSelected(id);
		}
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		String param = "bRef";
		if (!Service.isBouquet(mRef))
			param = "sRef";
		params.add(new NameValuePair(param, mRef));
		return params;
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		// Hub pages no longer start this loader. BaseHttpRecyclerFragment still requires LoaderCallbacks.
		return new AsyncListLoader(getAppCompatActivity(), new EventListRequestHandler(URIStore.EPG_NOW), false, args);
	}

	@Override
	public void onLoadFinished(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   @NonNull LoaderResult<ArrayList<ExtendedHashMap>> result) {
		// Unused: rows come from GetEpgNowNextTask / EnigmaClient.
	}

	@Override
	protected void reload() {
		mReload = false;
		if (mRows.isEmpty())
			setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
		else
			setEmptyText(null);
		loadEpgNowNext();
	}

	private void loadEpgNowNext() {
		mHttpHelper.onLoadStarted();
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getString(R.string.loading));
		}
		if (mEpgNowNextTask != null) {
			mEpgNowNextTask.cancel(true);
		}
		mEpgNowNextTask = new GetEpgNowNextTask(this);
		mEpgNowNextTask.execute(getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID));
	}

	@Override
	public void onEpgNowNextReady(boolean success, @NonNull List<ServiceNowNext> rows, @Nullable String errorText) {
		mHttpHelper.onLoadFinished();
		getAppCompatActivity().supportInvalidateOptionsMenu();
		mRows.clear();
		mListState.replaceAll(java.util.Collections.emptyList());
		if (!success) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(mName);
		}

		if (rows.isEmpty()) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			mRows.addAll(rows);
			mListState.replaceAll(ServiceListMapperKt.serviceListItemsFromNowNext(mRows));
		}
	}

	public void upOrReload() {
		if (!mHistory.isEmpty()) {
			Bundle args = getArguments();
			if (args != null) {
				mRef = args.getString(Service.KEY_REFERENCE, mRef);
				mName = args.getString(Service.KEY_NAME, mName);
			}
			mHistory.clear();
		}
		reload();
	}

	/**
	 * @param ref The ServiceReference to catch the EPG for
	 * @param nam The name of the Service for the reference
	 */
	public void openEpg(String ref, String nam) {
		ServiceEpgListFragment f = new ServiceEpgListFragment();
		ExtendedHashMap map = new ExtendedHashMap();
		map.put(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE, ref);
		map.put(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME, nam);
		Bundle args = new Bundle();
		args.putSerializable(sData, map);
		f.setArguments(args);
		getMultiPaneHandler().showDetails(f, true);
	}

	public void showPopupMenu(@NonNull View v, @NonNull ServiceNowNext row) {
		PopupMenu menu = new PopupMenu(getAppCompatActivity(), v);
		menu.getMenuInflater().inflate(R.menu.popup_servicelist, menu.getMenu());
		menu.getMenu().findItem(R.id.menu_next_event).setVisible(DreamDroid.featureNowNext() && row.getNext() != null);

		menu.setOnMenuItemClickListener(menuItem -> {
			String ref = row.getServiceReference();
			String name = row.getServiceName();
			switch (menuItem.getItemId()) {
				case R.id.menu_next_event: {
					Event next = row.getNext();
					if (next != null) {
						mCurrentItem = EpgListMapper.toExtendedHashMap(next);
						EpgDetailBottomSheet epgDialog = EpgDetailBottomSheet.newInstance(next);
						getMultiPaneHandler().showDialogFragment(epgDialog, "epg_detail_dialog");
					}
					break;
				}
				case R.id.menu_current_event: {
					Event now = row.getNow();
					if (now != null) {
						mCurrentItem = EpgListMapper.toExtendedHashMap(now);
						EpgDetailBottomSheet epgDialog = EpgDetailBottomSheet.newInstance(now);
						getMultiPaneHandler().showDialogFragment(epgDialog, "epg_detail_dialog");
					}
					break;
				}
				case R.id.menu_browse_epg:
					openEpg(ref, name);
					break;
				case R.id.menu_zap:
					zapTo(ref);
					break;
				case R.id.menu_stream:
					try {
						startActivity(IntentFactory.getStreamServiceIntent(
								getAppCompatActivity(),
								ref,
								name,
								mRef,
								ServiceListMapperKt.serviceNowNextToExtendedHashMap(row)));
					} catch (ActivityNotFoundException e) {
						showToast(getText(R.string.missing_stream_player));
					}
					break;
				default:
					return false;
			}
			return true;
		});
		menu.show();
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if (action < Statics.ACTION_SET_TIMER || action > Statics.ACTION_FIND_SIMILAR)
			return;
		// Typed detail opens with mCurrentItem already set to the selected event hash.
		ExtendedHashMap event = mCurrentItem;
		switch (action) {
			case Statics.ACTION_SET_TIMER:
				setTimerById(event);
				break;
			case Statics.ACTION_EDIT_TIMER:
				setTimerByEventData(event);
				break;
			case Statics.ACTION_FIND_SIMILAR:
				mHttpHelper.findSimilarEvents(event);
				break;
			case Statics.ACTION_IMDB:
				IntentFactory.queryIMDb(getAppCompatActivity(), event);
				break;
		}
	}
}
