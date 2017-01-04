/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.ServiceAdapter;
import net.reichholf.dreamdroid.adapter.recyclerview.SimpleTextAdapter;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerEventFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailDialog;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.RecyclerViewPauseOnScrollListener;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.SyncService;
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;


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
public class ServiceListFragment extends BaseHttpRecyclerEventFragment {
	private static final int LOADER_BOUQUETLIST_ID = 1;

	public static final String SERVICE_REF_ROOT = "root";
	public static final String BUNDLE_KEY_CURRENT_SERVICE = "currentService";
	public static final String BUNDLE_KEY_NAVNAME = "navname";
	public static final String BUNDLE_KEY_NAVREFERENCE = "navreference";
	public static final String BUNDLE_KEY_DETAILNAME = "detailname";
	public static final String BUNDLE_KEY_DETAILREFERENCE = "detailreference";
	public static final String BUNDLE_KEY_HISTORY = "history";
	public static final String BUNDLE_KEY_NAVITEMS = "navitems";
	public static final String BUNDLE_KEY_DETAILITEMS = "detailitems";

	private boolean mPickMode;
	private boolean mReload;

	private ListView mNavList;
	private RecyclerView mDetailList;
	private View mEmpty;

	private String mCurrentTitle;
	private String mNavReference;
	private String mNavName;
	private String mDetailReference;
	private String mDetailName;

	private ArrayList<ExtendedHashMap> mHistory;
	private Bundle mExtras;
	private ExtendedHashMap mData;
	private ArrayList<ExtendedHashMap> mNavItems;
	private ArrayList<ExtendedHashMap> mDetailItems;
	private ExtendedHashMap mCurrentService;
	private SlidingPaneLayout mSlidingPane;
	private ArrayList<NameValuePair> mNavHttpParams;
	private ArrayList<NameValuePair> mDetailHttpParams;

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mCurrentTitle = getString(R.string.services);
		mReload = true;
		mExtras = getArguments();
		String mode = null;

		if (mExtras != null) {
			mode = mExtras.getString("action");

			HashMap<String, Object> map = (HashMap<String, Object>) mExtras.getSerializable(sData);
			if (map != null) {
				mData = new ExtendedHashMap();
				mData.putAll(map);
			}
		} else {
			mExtras = new Bundle();
		}

		mPickMode = Intent.ACTION_PICK.equals(mode);

		if (savedInstanceState != null && !mPickMode) {
			mNavName = savedInstanceState.getString(BUNDLE_KEY_NAVNAME);
			mNavReference = savedInstanceState.getString(BUNDLE_KEY_NAVREFERENCE);
			mDetailName = savedInstanceState.getString(BUNDLE_KEY_DETAILNAME);
			mDetailReference = savedInstanceState.getString(BUNDLE_KEY_DETAILREFERENCE);

			mHistory = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_HISTORY);
			mNavItems = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_NAVITEMS);
			mDetailItems = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, BUNDLE_KEY_DETAILITEMS);

			mCurrentService = ExtendedHashMapHelper.restoreFromBundle(savedInstanceState, BUNDLE_KEY_CURRENT_SERVICE);

			mReload = false;
		} else {
			mHistory = new ArrayList<>();
			if (!SERVICE_REF_ROOT.equals(mNavReference)) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.put(Event.KEY_SERVICE_REFERENCE, mNavReference);
				map.put(Event.KEY_SERVICE_NAME, mNavName);

				mHistory.add(map);

				mNavItems = new ArrayList<>();
				mDetailItems = new ArrayList<>();
			}
		}

		if (mDetailReference == null) {
			mDetailReference = DreamDroid.getCurrentProfile().getDefaultRef();
			mDetailName = DreamDroid.getCurrentProfile().getDefaultRefName();
		}

		if( mNavReference == null ){
			mNavReference = DreamDroid.getCurrentProfile().getDefaultRef2();
			mNavName = DreamDroid.getCurrentProfile().getDefaultRef2Name();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.dual_list_view, container, false);
		mEmpty = v.findViewById(android.R.id.empty);

		mNavList = (ListView) v.findViewById(android.R.id.list);
		mDetailList = (RecyclerView) v.findViewById(R.id.list2);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		((AutofitRecyclerView) mDetailList).setMaxSpanCount(
				Integer.parseInt(
						prefs.getString(
								DreamDroid.PREFS_KEY_GRID_MAX_COLS,
								Integer.toString(AutofitRecyclerView.DEFAULT_MAX_SPAN_COUNT)
						)
				)
		);

		mNavList.setFastScrollEnabled(true);
		//TODO Detaillist FastScroll??!
		RecyclerViewPauseOnScrollListener listener = new RecyclerViewPauseOnScrollListener(Statics.TAG_PICON, true, true);
		mDetailList.addOnScrollListener(listener);

		mSlidingPane = (SlidingPaneLayout) v.findViewById(R.id.sliding_pane);
		mSlidingPane.setPanelSlideListener(new SlidingPaneLayout.PanelSlideListener() {
			@Override
			public void onPanelSlide(View panel, float slideOffset) {
			}

			@Override
			public void onPanelOpened(View panel) {
				mNavList.setEnabled(true);
			}

			@Override
			public void onPanelClosed(View panel) {
				mNavList.setEnabled(false);
			}
		});

		if (mDetailReference == null || "".equals(mDetailReference))
			mSlidingPane.openPane();

		setAdapter();
		return v;
	}


	@Override
	public RecyclerView getRecyclerView() {
		return (RecyclerView) getView().findViewById(R.id.list2);
	}

	/**
	 * @param key
	 * @return
	 */
	public String getDataForKey(String key) {
		if (mData != null) {
			return (String) mData.get(key);
		}

		return null;
	}

	/**
	 * @param key
	 * @param dfault
	 * @return
	 */
	public String getDataForKey(String key, String dfault) {
		if (mData != null) {
			String str = (String) mData.get(key);
			if (str != null) {
				return str;
			}
		}
		return dfault;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getAppCompatActivity().supportInvalidateOptionsMenu();
		getAppCompatActivity().setTitle(mCurrentTitle);

		mNavList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int position, long id) {
				onNavItemClick(a, position);
			}
		});

		if (mReload) {
			if(mNavReference != null && ! "".equals(mNavReference))
				reloadNav();
			else
				loadNavRoot();
			reloadDetail(false);
		} else {
			getAppCompatActivity().setTitle(mDetailName);
		}
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		onDetailItemClick(parent, view, position, false);
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, View view, int position, long id) {
		onDetailItemClick(parent, view, position, true);
		return true;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString(BUNDLE_KEY_NAVNAME, mNavName);
		outState.putString(BUNDLE_KEY_NAVREFERENCE, mNavReference);
		outState.putString(BUNDLE_KEY_DETAILNAME, mDetailName);
		outState.putString(BUNDLE_KEY_DETAILREFERENCE, mDetailReference);
		outState.putSerializable(BUNDLE_KEY_HISTORY, mHistory);
		outState.putSerializable(BUNDLE_KEY_NAVITEMS, mNavItems);
		outState.putSerializable(BUNDLE_KEY_DETAILITEMS, mDetailItems);
		outState.putParcelable(BUNDLE_KEY_CURRENT_SERVICE, mCurrentService);

		super.onSaveInstanceState(outState);
	}

	public String genWindowTitle(String title) {
		return title + " - " + mNavName;
	}

	/**
	 *
	 */
	private void setAdapter() {
		ListAdapter adapter = new SimpleAdapter(getAppCompatActivity(), mNavItems, android.R.layout.simple_list_item_1,
				new String[]{Event.KEY_SERVICE_NAME}, new int[]{android.R.id.text1});
		mNavList.setAdapter(adapter);
		if(mPickMode){
			SimpleTextAdapter simpleTextAdapter = new SimpleTextAdapter(mDetailItems, R.layout.simple_list_item_1, new String[]{Event.KEY_SERVICE_NAME}, new int[]{android.R.id.text1});
			mDetailList.setAdapter(simpleTextAdapter);
		} else {
			ServiceAdapter serviceAdapter = new ServiceAdapter(getAppCompatActivity(), mDetailItems);
			mDetailList.setAdapter(serviceAdapter);
		}
	}

	public void checkMenuReload(Menu menu, MenuInflater inflater) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity());
		if (sp.getBoolean("disable_fab_reload", false)) {
			detachFabReload();
			inflater.inflate(R.menu.reload, menu);
		} else {
			connectFabReload();
		}
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.servicelist, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		if (getMultiPaneHandler().isDrawerOpen())
			return;

		MenuItem setDefault = menu.findItem(R.id.menu_default);
		String defaultReference = DreamDroid.getCurrentProfile().getDefaultRef();
		setDefault.setVisible(true);
		if (defaultReference != null) {
			if (defaultReference.equals(mDetailReference)) {
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
			case Statics.ITEM_OVERVIEW:
				if (!mSlidingPane.isOpen()) {
					mSlidingPane.openPane();
					return true;
				}
				if ((mSlidingPane.isOpen() || !mSlidingPane.isSlideable()) && !SERVICE_REF_ROOT.equals(mNavReference)) {
					mNavReference = SERVICE_REF_ROOT;
					mNavName = (String) getText(R.string.bouquet_overview);
					reloadNav();
					return true;
				}
				mSlidingPane.closePane();
				return true;
			case Statics.ITEM_SET_DEFAULT:
				if (mDetailReference != null || mNavReference != null) {
					Profile p = DreamDroid.getCurrentProfile();
					boolean reset = false;
					if (p.getDefaultRef() != null && p.getDefaultRef().equals(mDetailReference)) {
						p.setDefaultRef(null);
						reset = true;
					}
					if (p.getDefaultRef2() != null && p.getDefaultRef2().equals(mNavReference)) {
						p.setDefaultRef2(null);
						reset = true;
					}

					if (!reset) {
						if (mDetailReference != null)
							p.setDefaultRefValues(mDetailReference, mDetailName);
						if (mNavReference != null)
							p.setDefaultRef2Values(mNavReference, mNavName);
					}
					DatabaseHelper dbh = DatabaseHelper.getInstance(getAppCompatActivity());
					if (dbh.updateProfile(p)) {
						if (!reset)
							showToast(getText(R.string.default_bouquet_set_to) + " '" + mDetailName + "'");
					} else {
						showToast(getText(R.string.default_bouquet_not_set));
					}
				} else {
					showToast(getText(R.string.default_bouquet_not_set));
				}
				getAppCompatActivity().supportInvalidateOptionsMenu();
				return true;
			case Statics.ITEM_SYNC_EPG:
				syncEpg();
				return true;
			default:
				return super.onItemSelected(id);
		}
	}

	private void syncEpg() {
		Intent intent = new Intent(getActivity().getApplicationContext(), SyncService.class);
		intent.putExtra(Event.KEY_SERVICE_REFERENCE, mDetailReference);
		getActivity().startService(intent);
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		switch (loader) {
			case HttpFragmentHelper.LOADER_DEFAULT_ID:
				return mDetailHttpParams;
			case LOADER_BOUQUETLIST_ID:
				return mNavHttpParams;
			default:
				return super.getHttpParams(loader);
		}
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AbstractListRequestHandler handler;
		if (id == LOADER_BOUQUETLIST_ID || mPickMode) {
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
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {
		getAppCompatActivity().supportInvalidateOptionsMenu();

		if (loader.getId() == LOADER_BOUQUETLIST_ID) {
			mNavItems.clear();
			((BaseAdapter) mNavList.getAdapter()).notifyDataSetChanged();
		} else {
			mDetailItems.clear();
			mDetailList.getAdapter().notifyDataSetChanged();
		}

		String title = mNavName;
		if (isDetailAvail())
			title = mDetailName;

		mHttpHelper.finishProgress(title);

		if (result.isError()) {
			setEmptyText(result.getErrorText());
			return;
		}

		ArrayList<ExtendedHashMap> list = result.getResult();
		if (list == null) {
			showToast(getString(R.string.error));
			return;
		}

		if (loader.getId() == LOADER_BOUQUETLIST_ID) {
			mNavItems.addAll(list);
			((BaseAdapter) mNavList.getAdapter()).notifyDataSetChanged();
		} else {
			mEmpty.setVisibility(View.GONE);
			mDetailList.setVisibility(View.VISIBLE);
			mDetailItems.addAll(list);
			mDetailList.getAdapter().notifyDataSetChanged();
		}
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

	private void onNavItemClick(View l, int position) {
		ExtendedHashMap item = mNavItems.get(position);
		final String ref = item.getString(Event.KEY_SERVICE_REFERENCE);
		final String nam = item.getString(Event.KEY_SERVICE_NAME);
		if (Service.isMarker(ref) || !Service.isBouquet(ref))
			return;
		// without FROM it's a "all" reference
		if (SERVICE_REF_ROOT.equals(mNavReference) && ref.toUpperCase().contains("FROM")) {
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(Event.KEY_SERVICE_REFERENCE, String.valueOf(ref));
			map.put(Event.KEY_SERVICE_NAME, String.valueOf(nam));
			mHistory.add(map);
			mNavReference = ref;
			mNavName = nam;
			mNavList.setSelectionAfterHeaderView();
			reloadNav();
		} else {
			mDetailReference = ref;
			mDetailName = nam;
			reloadDetail(false);
		}
	}

	private void onDetailItemClick(View l, View v, int position, boolean isLong) {
		ExtendedHashMap item = mDetailItems.get(position);
		final String ref = item.getString(Event.KEY_SERVICE_REFERENCE);
		final String nam = item.getString(Event.KEY_SERVICE_NAME);
		if (Service.isMarker(ref))
			return;

		if (mPickMode) {
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(Event.KEY_SERVICE_REFERENCE, ref);
			map.put(Event.KEY_SERVICE_NAME, nam);

			Intent intent = new Intent();
			intent.putExtra(sData, (Serializable) map);
			finish(Activity.RESULT_OK, intent);
		} else {
			boolean instantZap = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity()).getBoolean(
					"instant_zap", false);
			if ((instantZap && !isLong) || (!instantZap && isLong)) {
				zapTo(ref);
			} else {
				mCurrentService = item;
				showPopupMenu(v);
			}
		}
	}

	public void showPopupMenu(View v) {
		PopupMenu menu = new PopupMenu(getAppCompatActivity(), v);
		menu.getMenuInflater().inflate(R.menu.popup_servicelist, menu.getMenu());
		menu.getMenu().findItem(R.id.menu_next_event).setVisible(DreamDroid.featureNowNext());

		menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem menuItem) {
				String ref = mCurrentService.getString(Service.KEY_REFERENCE);
				String name = mCurrentService.getString(Service.KEY_NAME);
				boolean showNext = false;
				switch (menuItem.getItemId()) {
					case R.id.menu_next_event:
						showNext = true;
					case R.id.menu_current_event:
						Bundle args = new Bundle();
						args.putParcelable("currentItem", mCurrentService);
						args.putBoolean("showNext", showNext);
						getMultiPaneHandler().showDialogFragment(EpgDetailDialog.class, args, "epg_detail_dialog");
						break;
					case R.id.menu_browse_epg:
						openEpg(ref, name);
						break;
					case R.id.menu_zap:
						zapTo(ref);
						break;
					case R.id.menu_stream:
						try {
							startActivity(IntentFactory.getStreamServiceIntent(getAppCompatActivity(), ref, name, mDetailReference, mCurrentService));
						} catch (ActivityNotFoundException e) {
							showToast(getText(R.string.missing_stream_player));
						}
						break;
					default:
						return false;
				}
				return true;
			}
		});
		menu.show();
	}

	public void reloadNav() {
		reload(mNavReference, true);
	}

	public void reloadDetail(boolean keepCurrent) {
		if (mDetailReference != null && !"".equals(mDetailReference)) {
			// Hide ListView show empty/progress
			if (!keepCurrent) {
				setEmptyText(getString(R.string.loading), R.drawable.ic_loading_48dp);
				mEmpty.setVisibility(View.VISIBLE);
				mDetailList.setVisibility(View.GONE);
				getAppCompatActivity().setTitle(mDetailName);
			}
			reload(mDetailReference, false);
		} else {
			setEmptyText(getString(R.string.no_list_item));
		}
	}

	@Override
	protected void reload() {
		reloadDetail(true);
	}

	public void reload(String ref, boolean isBouquetList) {
		mReload = false;
		mHttpHelper.onLoadStarted();

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Event.KEY_SERVICE_REFERENCE, String.valueOf(ref));
		data.put(Event.KEY_SERVICE_NAME, String.valueOf(ref));
		mExtras.putSerializable(sData, data);

		ArrayList<NameValuePair> params = new ArrayList<>();

		if (isBouquetList) {
			mSlidingPane.openPane();
			if (ref.equals(SERVICE_REF_ROOT)) {
				loadNavRoot();
				mHttpHelper.onLoadFinished();
				return;
			}
			params.add(new NameValuePair("sRef", ref));
			mNavHttpParams = params;
			mHttpHelper.reload(LOADER_BOUQUETLIST_ID);
		} else {
			if (DreamDroid.checkInitial(getAppCompatActivity(), DreamDroid.INITIAL_SERVICELIST_PANE)) {
				mSlidingPane.openPane();
				DreamDroid.setNotInitial(getAppCompatActivity(), DreamDroid.INITIAL_SERVICELIST_PANE);
			} else {
				mSlidingPane.closePane();
			}
			String param = "bRef";
			if (mPickMode)
				param = "sRef";
			params.add(new NameValuePair(param, ref));

			mDetailHttpParams = params;
			mHttpHelper.reload(HttpFragmentHelper.LOADER_DEFAULT_ID);
		}
	}

	public void loadNavRoot() {
		getAppCompatActivity().setTitle(getString(R.string.services));

		mNavItems.clear();

		String[] servicelist = getResources().getStringArray(R.array.servicelist);
		String[] servicerefs = getResources().getStringArray(R.array.servicerefs);

		for (int i = 0; i < servicelist.length; i++) {
			ExtendedHashMap map = new ExtendedHashMap();
			map.put(Event.KEY_SERVICE_NAME, servicelist[i]);
			map.put(Event.KEY_SERVICE_REFERENCE, servicerefs[i]);
			mNavItems.add(map);
		}

		mNavReference = SERVICE_REF_ROOT;
		mNavName = "";
		getAppCompatActivity().supportInvalidateOptionsMenu();
		((BaseAdapter) mNavList.getAdapter()).notifyDataSetChanged();
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		super.onSimpleResult(success, result);
	}

	protected boolean isDetailAvail() {
		return !(mDetailReference == null) && !"".equals(mDetailReference.trim());
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		if(action < Statics.ACTION_SET_TIMER || action > Statics.ACTION_FIND_SIMILAR)
			return;
		boolean isNext = (Boolean) details;
		ExtendedHashMap event = isNext ? Event.fromNext(mCurrentService) : mCurrentService;
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
