/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
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
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerEventFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EpgNowNextListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.widget.AutofitRecyclerView;

import java.util.ArrayList;


/**
 * Handles ServiceLists of (based on service references).
 * <p/>
 * If called with Intent.ACTION_PICK it can be used for selecting services (e.g.
 * to set a timer).<br/>
 * In Pick-Mode no EPG will be loaded/shown.<br/>
 * For any other action it will be a full-featured ServiceList Browser capable
 * of showing EPG of running events or calling a
 * <code>ServiceEpgListActivity</code> to show the whole EPG of a service
 *
 * @author sreichholf
 */
public class ServiceListPageFragment extends BaseHttpRecyclerEventFragment {
	@Nullable
	private static final String TAG = ServiceListPageFragment.class.getCanonicalName();
	private static final int LOADER_BOUQUETLIST_ID = 1;

	@Nullable
	@State
	public String mName;
	@Nullable
	@State
	public String mRef;

	private ArrayList<ExtendedHashMap> mHistory;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mName = getString(R.string.services);
		mHasFabMain = false;
		mEnableReload = false;

		Bundle args = getArguments();
		if (args != null) {
			mRef = args.getString(Service.KEY_REFERENCE, mRef);
			mName = args.getString(Service.KEY_NAME, mName);
		}

		mCurrentItem = new ExtendedHashMap();
		mCurrentItem.put(Service.KEY_REFERENCE, mRef);
		mCurrentItem.put(Service.KEY_NAME, mName);
		mHistory = new ArrayList<>();

		if (mRef == null) {
			mRef = DreamDroid.getCurrentProfile().getDefaultRef();
			mName = DreamDroid.getCurrentProfile().getDefaultRefName();
		}
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
		return inflater.inflate(R.layout.card_grid_content, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		((AutofitRecyclerView) getRecyclerView()).setMaxSpanCount(
				Integer.parseInt(
						prefs.getString(
								DreamDroid.PREFS_KEY_GRID_MAX_COLS,
								Integer.toString(AutofitRecyclerView.DEFAULT_MAX_SPAN_COUNT)
						)
				)
		);
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		reload();
	}

	@Override
	public void onItemClick(RecyclerView parent, @NonNull View view, int position, long id) {
		onItemClick(parent, view, position, false);
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, @NonNull View view, int position, long id) {
		onItemClick(parent, view, position, true);
		return true;
	}

	private void onItemClick(View l, @NonNull View v, int position, boolean isLong) {
		ExtendedHashMap previousItem = mCurrentItem;
		mCurrentItem = mMapList.get(position);
		final String ref = mCurrentItem.getString(Event.KEY_SERVICE_REFERENCE);
		final String name = mCurrentItem.getString(Event.KEY_SERVICE_NAME);
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
			showPopupMenu(v);
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
		String defaultReference = DreamDroid.getCurrentProfile().getDefaultRef();
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
					if (p.getDefaultRef() != null && p.getDefaultRef().equals(mRef)) {
						p.setDefaultRef(null);
						reset = true;
					} else {
						p.setDefaultRefValues(mRef, mName);
					}

					DatabaseHelper dbh = DatabaseHelper.getInstance(getAppCompatActivity());
					if (dbh.updateProfile(p)) {
						if (!reset)
							showToast(getText(R.string.default_bouquet_set_to) + " '" + mName + "'");
					} else {
						showToast(getText(R.string.default_bouquet_not_set));
					}
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
		switch (loader) {
			case HttpFragmentHelper.LOADER_DEFAULT_ID:
				ArrayList<NameValuePair> params = new ArrayList();
				String param = "bRef";
				if (!Service.isBouquet(mRef))
					param = "sRef";
				params.add(new NameValuePair(param, mRef));
				return params;
			default:
				return super.getHttpParams(loader);
		}
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AbstractListRequestHandler handler;
		if (id == LOADER_BOUQUETLIST_ID) {
			handler = new ServiceListRequestHandler();
		} else {
			if (DreamDroid.featureNowNext())
				handler = new EpgNowNextListRequestHandler();
			else
				handler = new EventListRequestHandler(URIStore.EPG_NOW);
		}
		return new AsyncListLoader(getAppCompatActivity(), handler, true, args);
	}

	@Override
	public void onLoadFinished(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   @NonNull LoaderResult<ArrayList<ExtendedHashMap>> result) {
		getAppCompatActivity().supportInvalidateOptionsMenu();

		if (!isResumed())
			return;
		super.onLoadFinished(loader, result);
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
		map.put(Event.KEY_SERVICE_REFERENCE, ref);
		map.put(Event.KEY_SERVICE_NAME, nam);
		Bundle args = new Bundle();
		args.putSerializable(sData, map);
		f.setArguments(args);
		getMultiPaneHandler().showDetails(f, true);
	}

	public void showPopupMenu(@NonNull View v) {
		PopupMenu menu = new PopupMenu(getAppCompatActivity(), v);
		menu.getMenuInflater().inflate(R.menu.popup_servicelist, menu.getMenu());
		menu.getMenu().findItem(R.id.menu_next_event).setVisible(DreamDroid.featureNowNext());

		menu.setOnMenuItemClickListener(menuItem -> {
			String ref = mCurrentItem.getString(Service.KEY_REFERENCE);
			String name = mCurrentItem.getString(Service.KEY_NAME);
			boolean showNext = false;
			switch (menuItem.getItemId()) {
				case R.id.menu_next_event:
					showNext = true;
				case R.id.menu_current_event:
					EpgDetailBottomSheet epgDialog = EpgDetailBottomSheet.newInstance(mCurrentItem, showNext);
					getMultiPaneHandler().showDialogFragment(epgDialog, "epg_detail_dialog");
					break;
				case R.id.menu_browse_epg:
					openEpg(ref, name);
					break;
				case R.id.menu_zap:
					zapTo(ref);
					break;
				case R.id.menu_stream:
					try {
						startActivity(IntentFactory.getStreamServiceIntent(getAppCompatActivity(), ref, name, mRef, mCurrentItem));
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
		boolean isNext = (Boolean) details;
		ExtendedHashMap event = isNext ? Event.fromNext(mCurrentItem) : mCurrentItem;
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
