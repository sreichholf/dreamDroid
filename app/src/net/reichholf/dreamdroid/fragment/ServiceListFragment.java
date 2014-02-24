/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.ServiceListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpEventListFragment;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.fragment.helper.DreamDroidHttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ExtendedHashMapHelper;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EpgNowNextListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.EventListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces.ListRequestInterface;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.PauseOnScrollListener;

/**
 * Handles ServiceLists of (based on service references).
 * 
 * If called with Intent.ACTION_PICK it can be used for selecting services (e.g.
 * to set a timer).<br/>
 * In Pick-Mode no EPG will be loaded/shown.<br/>
 * For any other action it will be a full-featured ServiceList Browser capable
 * of showing EPG of running events or calling a
 * <code>ServiceEpgListActivity</code> to show the whole EPG of a service
 * 
 * @author sreichholf
 * 
 */
public class ServiceListFragment extends AbstractHttpEventListFragment implements ActionDialog.DialogActionListener {
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
	private ListView mDetailList;
	private View mEmpty;
	private ProgressDialog mProgress;

	private String mBaseTitle;
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
		mCurrentTitle = mBaseTitle = getString(R.string.services);
		mReload = true;
		Bundle args = getArguments();
		String mode = null;

		if (args != null) {
			mode = args.getString("action");
		}

		if (Intent.ACTION_PICK.equals(mode)) {
			mPickMode = true;
		} else {
			mPickMode = false;
		}

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
			mHistory = new ArrayList<ExtendedHashMap>();
			if (!SERVICE_REF_ROOT.equals(mNavReference)) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.put(Event.KEY_SERVICE_REFERENCE, mNavReference);
				map.put(Event.KEY_SERVICE_NAME, mNavName);

				mHistory.add(map);

				mExtras = getArguments();
				mNavItems = new ArrayList<ExtendedHashMap>();
				mDetailItems = new ArrayList<ExtendedHashMap>();
			}
		}

		if (mDetailReference == null) {
			mDetailReference = DreamDroid.getCurrentProfile().getDefaultRef();
			mDetailName = DreamDroid.getCurrentProfile().getDefaultRefName();
		}

		// if( mNavReference == null ){
		// mNavReference = DreamDroid.getCurrentProfile().getDefaultRef2();
		// mNavName = DreamDroid.getCurrentProfile().getDefaultRef2Name();
		// }

		if (mExtras != null) {
			HashMap<String, Object> map = (HashMap<String, Object>) mExtras.getSerializable("data");
			if (map != null) {
				mData = new ExtendedHashMap();
				mData.putAll(map);
			}
		} else {
			mExtras = new Bundle();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.dual_list_view, null, false);

		mEmpty = v.findViewById(android.R.id.empty);

		mNavList = (ListView) v.findViewById(android.R.id.list);
		mDetailList = (ListView) v.findViewById(R.id.list2);

		// Some may call this a Hack, but I think it a proper solution
		// On devices with resolutions other than xlarge, there is no second
		// ListView (@id/listView2).
		// So we just use the only one available and the rest will work as
		// normal with almost no additional adjustments.
		if (mDetailList == null) {
			mDetailList = mNavList;
			mDetailItems = mNavItems;
		}

		mNavList.setFastScrollEnabled(true);
		mDetailList.setFastScrollEnabled(true);

		PauseOnScrollListener listener = new PauseOnScrollListener(ImageLoader.getInstance(), false, true);
		mDetailList.setOnScrollListener(listener);

		mSlidingPane = (SlidingPaneLayout) v.findViewById(R.id.sliding_pane);
		if (mSlidingPane != null) {
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
		}

		setAdapter();
		return v;
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
		getActionBarActivity().supportInvalidateOptionsMenu();
		getActionBarActivity().setTitle(mCurrentTitle);

		mNavList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int position, long id) {
				onListItemClick((ListView) a, v, position, id);
			}
		});

		mDetailList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> a, View v, int position, long id) {
				onListItemClick((ListView) a, v, position, id);
			}
		});

		mNavList.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
				return onListItemLongClick((ListView) a, v, position, id);
			}
		});

		mDetailList.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
				return onListItemLongClick((ListView) a, v, position, id);
			}
		});

		if (mReload) {
			loadNavRoot();
			reloadDetail(false);
		} else {
			getActionBarActivity().setTitle(mDetailName);
		}
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

	@Override
	public void onResume() {
		super.onResume();
	}

	public String genWindowTitle(String title) {
		return title + " - " + mNavName;
	}

	/**
	 * 
	 */
	private void setAdapter() {
		ListAdapter adapter;
		if (!mNavList.equals(mDetailList)) {
			adapter = new SimpleAdapter(getActionBarActivity(), mNavItems, android.R.layout.simple_list_item_1,
					new String[] { Event.KEY_SERVICE_NAME }, new int[] { android.R.id.text1 });
			mNavList.setAdapter(adapter);
		}
		adapter = new ServiceListAdapter(getActionBarActivity(), mDetailItems);
		mDetailList.setAdapter(adapter);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// back to standard back-button-behaviour when we're already at root
		// level
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (!SERVICE_REF_ROOT.equals(mNavReference) && mHistory != null) {
				int idx = mHistory.size() - 1;
				if (idx >= 0) {
					ExtendedHashMap map = null;

					try {
						map = (mHistory.get(idx));
					} catch (ClassCastException ex) {
						return super.onKeyDown(keyCode, event);
					}
					if (map != null) {
						String oldref = map.getString(Event.KEY_SERVICE_REFERENCE);
						String oldname = map.getString(Event.KEY_SERVICE_NAME);

						if (!mNavReference.equals(oldref) && oldref != null) {
							mNavReference = oldref;
							mNavName = oldname;
							mHistory.remove(idx);
							reloadNav();
							return true;
						}
					}
				}
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	public void onListItemClick(ListView l, View v, int position, long id) {
		onListItemClick(l, v, position, id, false);
	}

	/**
	 * @param l
	 * @param v
	 * @param position
	 * @param id
	 * @return
	 */
	protected boolean onListItemLongClick(ListView l, View v, int position, long id) {
		onListItemClick(l, v, position, id, true);

		return true;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.reload, menu);
		inflater.inflate(R.menu.servicelist, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		MenuItem overview = menu.findItem(R.id.menu_overview);
		if (!SERVICE_REF_ROOT.equals(mNavReference) || mNavList.equals(mDetailList)) {
			overview.setVisible(true);
		} else {
			overview.setVisible(false);
		}

		MenuItem setDefault = menu.findItem(R.id.menu_default);
		String defaultReference = DreamDroid.getCurrentProfile().getDefaultRef();
		setDefault.setVisible(true);
		if (defaultReference != null) {
			if (defaultReference.equals(mDetailReference)) {
				setDefault.setVisible(false);
			}
		}

		MenuItem reload = menu.findItem(R.id.menu_reload);
		if (!mPickMode) {
			reload.setVisible(true);
		} else {
			reload.setVisible(false);
		}
	}

	@Override
	protected boolean onItemClicked(int id) {
		switch (id) {
		case Statics.ITEM_OVERVIEW:
			mNavReference = SERVICE_REF_ROOT;
			mNavName = (String) getText(R.string.bouquet_overview);
			reloadNav();
			return true;
		case Statics.ITEM_SET_DEFAULT:
			if (mDetailReference != null || mNavReference != null) {
				Profile p = DreamDroid.getCurrentProfile();
				if (mDetailReference != null)
					p.setDefaultRefValues(mDetailReference, mDetailName);
				if (mNavReference != null)
					p.setDefaultRef2Values(mNavReference, mNavName);

				DatabaseHelper dbh = DatabaseHelper.getInstance(getActionBarActivity());
				if (dbh.updateProfile(p)) {
					showToast(getText(R.string.default_bouquet_set_to) + " '" + mDetailName + "'");
				} else {
					showToast(getText(R.string.default_bouquet_not_set));
				}
			} else {
				showToast(getText(R.string.default_bouquet_not_set));
			}
			getActionBarActivity().supportInvalidateOptionsMenu();
			return true;
		case Statics.ITEM_RELOAD:
			if (!mNavList.equals(mDetailList))
				reloadNav();
			reloadDetail(true);
			return true;
		default:
			return super.onItemClicked(id);
		}
	}

	@Override
	protected ArrayList<NameValuePair> getHttpParams(int loader) {
		switch(loader){
			case DreamDroidHttpFragmentHelper.LOADER_DEFAULT_ID:
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
		if(id == LOADER_BOUQUETLIST_ID){
			handler = new ServiceListRequestHandler();
		}  else {
			if (DreamDroid.featureNowNext())
				handler = new EpgNowNextListRequestHandler();
			else
				handler = new EventListRequestHandler(URIStore.EPG_NOW);
		}
		return new AsyncListLoader(getActionBarActivity(), handler, true, args);
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {
		getActionBarActivity().supportInvalidateOptionsMenu();

		if (loader.getId() == LOADER_BOUQUETLIST_ID) {
			mNavItems.clear();
		} else {
			mDetailItems.clear();
		}

		if (result.isError()) {
			setEmptyText(result.getErrorText());
			return;
		}

		String title = mNavName;
		if(isDetailAvail())
			title = mDetailName;

		mHttpHelper.finishProgress(title);
		ArrayList<ExtendedHashMap> list = result.getResult();
		if(list == null){
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
			((BaseAdapter) mDetailList.getAdapter()).notifyDataSetChanged();
		}
	}


	/**
	 * @param ref
	 *            The ServiceReference to catch the EPG for
	 * @param nam
	 *            The name of the Service for the reference
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

	/**
	 * @param l
	 * @param v
	 * @param position
	 * @param id
	 * @param isLong
	 */
	private void onListItemClick(ListView l, View v, int position, long id, boolean isLong) {
		@SuppressWarnings("unchecked")
		ExtendedHashMap item = new ExtendedHashMap((HashMap<String, Object>) l.getItemAtPosition(position));
		final String ref = item.getString(Event.KEY_SERVICE_REFERENCE);
		final String nam = item.getString(Event.KEY_SERVICE_NAME);
		if (Service.isBouquet(ref)) {
			if (l.equals(mNavList)) {
				// without FROM it's a "all" reference
				if (SERVICE_REF_ROOT.equals(mNavReference) && ref.toUpperCase().contains("FROM")) { 
					ExtendedHashMap map = new ExtendedHashMap();
					map.put(Event.KEY_SERVICE_REFERENCE, String.valueOf(ref));
					map.put(Event.KEY_SERVICE_NAME, String.valueOf(nam));

					mHistory.add(map);
					mNavReference = ref;
					mNavName = nam;
					reloadNav();
				} else {
					mDetailReference = ref;
					mDetailName = nam;
					reloadDetail(false);
				}
			}
			mBaseTitle = nam;
		} else { // It's a listitem in the servicelist
			if (mPickMode) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.put(Event.KEY_SERVICE_REFERENCE, ref);
				map.put(Event.KEY_SERVICE_NAME, nam);

				Intent intent = new Intent();
				intent.putExtra(sData, (Serializable) map);
				finish(Activity.RESULT_OK, intent);
			} else {
				boolean instantZap = PreferenceManager.getDefaultSharedPreferences(getActionBarActivity()).getBoolean(
						"instant_zap", false);
				if ((instantZap && !isLong) || (!instantZap && isLong)) {
					zapTo(ref);
				} else {
					mCurrentService = item;

					CharSequence[] actions = { getText(R.string.current_event), getText(R.string.browse_epg),
							getText(R.string.zap), getText(R.string.stream) };
					int[] actionIds = { Statics.ACTION_CURRENT, Statics.ACTION_EPG, Statics.ACTION_ZAP,
							Statics.ACTION_STREAM };

					getMultiPaneHandler().showDialogFragment(
							SimpleChoiceDialog.newInstance(mCurrentService.getString(Event.KEY_SERVICE_NAME), actions,
									actionIds), "service_action_dialog");
				}
			}
		}
	}

	public void reloadNav() {
		reload(mNavReference, true);
	}

	public void reloadDetail(boolean keepCurrent) {
		if (mDetailReference != null && !"".equals(mDetailReference)) {
			// Hide ListView show empty/progress
			if (!keepCurrent) {
				mEmpty.setVisibility(View.VISIBLE);
				mDetailList.setVisibility(View.GONE);
				getActionBarActivity().setTitle(mDetailName);
			}
			reload(mDetailReference, false);
		}
	}

	@Override
	public void reload(){
		if(mDetailReference != null && !mDetailReference.isEmpty())
			reloadDetail(true);
	}

	public void reload(String ref, boolean isBouquetList) {
		mReload = false;
		mHttpHelper.onLoadStarted();

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Event.KEY_SERVICE_REFERENCE, String.valueOf(ref));
		data.put(Event.KEY_SERVICE_NAME, String.valueOf(ref));
		mExtras.putSerializable(sData, data);

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		if (isBouquetList || mPickMode) {
			if(mSlidingPane != null)
				mSlidingPane.openPane();
			if (ref.equals(SERVICE_REF_ROOT)) {
				loadNavRoot();
				mHttpHelper.onLoadFinished();
				return;
			}
			params.add(new BasicNameValuePair("sRef", ref));
		} else {
			if(mSlidingPane != null)
				mSlidingPane.closePane();
			params.add(new BasicNameValuePair("bRef", ref));
		}

		if(isBouquetList){
			mNavHttpParams = params;
			mHttpHelper.reload(LOADER_BOUQUETLIST_ID);
		} else {
			mDetailHttpParams = params;
			mHttpHelper.reload(DreamDroidHttpFragmentHelper.LOADER_DEFAULT_ID);
		}
	}

	public void loadNavRoot() {
		getActionBarActivity().setTitle(getString(R.string.services));

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
		getActionBarActivity().supportInvalidateOptionsMenu();
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

	protected boolean isDetailAvail(){
		return !(mDetailReference == null) && !"".equals(mDetailReference.trim());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.fragment.dialogs.PrimitiveDialog.
	 * DialogActionListener#onDialogAction(int)
	 */
	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		String ref = mCurrentService.getString(Service.KEY_REFERENCE);
		String name = mCurrentService.getString(Service.KEY_NAME);

		switch (action) {
		case Statics.ACTION_CURRENT:
			Bundle args = new Bundle();
			args.putParcelable("currentItem", mCurrentService);
			getMultiPaneHandler().showDialogFragment(EpgDetailDialog.class, args, "epg_detail_dialog");
			break;

		case Statics.ACTION_EPG:
			openEpg(ref, name);
			break;

		case Statics.ACTION_ZAP:
			zapTo(ref);
			break;

		case Statics.ACTION_STREAM:
			try {
				startActivity(IntentFactory.getStreamServiceIntent(ref, name));
			} catch (ActivityNotFoundException e) {
				showToast(getText(R.string.missing_stream_player));
			}
			break;

		case Statics.ACTION_SET_TIMER:
			setTimerById(mCurrentService);
			break;

		case Statics.ACTION_EDIT_TIMER:
			setTimerByEventData(mCurrentService);
			break;

		case Statics.ACTION_FIND_SIMILAR:
			mHttpHelper.findSimilarEvents(mCurrentService);
			break;

		case Statics.ACTION_IMDB:
			IntentFactory.queryIMDb(getActionBarActivity(), mCurrentService);
			break;
		}
	}
}
