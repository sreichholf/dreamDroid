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
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
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
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ServiceListRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces.ListRequestInterface;
import net.reichholf.dreamdroid.intents.IntentFactory;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
public class ServiceListFragment extends AbstractHttpFragment implements ActionDialog.DialogActionListener {
	public static final String SERVICE_REF_ROOT = "root";

	private boolean mPickMode;
	private boolean mReload;

	private ListView mNavList;
	private ListView mDetailList;
	private TextView mDetailHeader;
	private View mEmpty;
	private ProgressDialog mProgress;

	private String mBaseTitle;
	private String mCurrentTitle;
	private String mNavReference;
	private String mNavName;
	private String mDetailReference;
	private String mDetailName;

	private ArrayList<ExtendedHashMap> mHistory;
	private GetServiceListTask mListTask;
	private ArrayList<GetServiceListTask> mListTasks;
	private Bundle mExtras;
	private ExtendedHashMap mData;
	private ArrayList<ExtendedHashMap> mNavItems;
	private ArrayList<ExtendedHashMap> mDetailItems;
	private ExtendedHashMap mCurrentService;

	protected abstract class AsyncListUpdateTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean> {
		protected ArrayList<ExtendedHashMap> mTaskList;

		protected ListRequestInterface mListRequestHandler;
		protected boolean mRequireLocsAndTags;
		protected ArrayList<String> mLocations;
		protected ArrayList<String> mTags;

		public AsyncListUpdateTask(String baseTitle) {
			mListRequestHandler = null;
		}

		public AsyncListUpdateTask(String baseTitle, ListRequestInterface listRequestHandler, boolean requireLocsAndTags) {
			mListRequestHandler = listRequestHandler;
			mRequireLocsAndTags = requireLocsAndTags;
		}

		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			if (mListRequestHandler == null) {
				throw new UnsupportedOperationException(
						"Method doInBackground not re-implemented while no ListRequestHandler has been given");
			}

			mTaskList = new ArrayList<ExtendedHashMap>();
			if (isCancelled())
				return false;
			publishProgress(getString(R.string.fetching_data));

			String xml = mListRequestHandler.getList(getHttpClient(), params[0]);
			if (xml != null) {
				if (isCancelled())
					return false;
				publishProgress(getString(R.string.parsing));

				mTaskList.clear();

				if (mListRequestHandler.parseList(xml, mTaskList)) {
					if (mRequireLocsAndTags) {
						if (DreamDroid.getLocations().size() == 0) {
							if (isCancelled())
								return false;
							publishProgress(getString(R.string.fetching_data));

							if (!DreamDroid.loadLocations(getHttpClient())) {
								// TODO Add Error-Msg when loadLocations fails
							}
						}

						if (DreamDroid.getTags().size() == 0) {
							if (isCancelled())
								return false;
							publishProgress(getString(R.string.fetching_data));

							if (!DreamDroid.loadTags(getHttpClient())) {
								// TODO Add Error-Msg when loadTags fails
							}
						}
					}
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * @author sreichholf Fetches a service list async. Does all the
	 *         error-handling, refreshing and title-setting
	 */
	private class GetServiceListTask extends AsyncListUpdateTask {
		ArrayList<NameValuePair> mParams;
		private boolean mIsBouquetList;

		public GetServiceListTask() {
			super(getString(R.string.services));
			mParams = null;
		}

		public void setParams(ArrayList<NameValuePair> params, boolean isBouquetList) {
			mIsBouquetList = isBouquetList;
			mParams = params;
		}

		public ArrayList<NameValuePair> getParams() {
			if (mParams != null) {
				return mParams;
			} else {
				return new ArrayList<NameValuePair>();
			}
		}

		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			mTaskList = new ArrayList<ExtendedHashMap>();
			if (isCancelled())
				return false;
			publishProgress(getString(R.string.fetching_data));

			String xml;
			AbstractListRequestHandler handler;
			if (mIsBouquetList || mPickMode) {
				handler = new ServiceListRequestHandler();
			} else {
				if (DreamDroid.featureNowNext())
					handler = new EpgNowNextListRequestHandler();
				else
					handler = new EventListRequestHandler(URIStore.EPG_NOW);
			}
			xml = handler.getList(getHttpClient(), params[0]);

			if (xml != null && !isCancelled()) {
				publishProgress(getString(R.string.parsing));
				boolean result = false;
				result = handler.parseList(xml, mTaskList);
				return result;

			}
			return false;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			if (!isCancelled())
				updateProgress(progress[0]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			String title = mDetailName == null ? mBaseTitle : mDetailName;

			if (result) {
				if (!mIsBouquetList && mDetailName != null)
					title = mDetailName;
				else if (mDetailHeader == null)
					title = mNavName;
			} else {
				title = getString(R.string.get_content_error);

				if (getHttpClient().hasError()) {
					showToast(getString(R.string.get_content_error) + "\n" + getHttpClient().getErrorText());
				}
			}

			if (mRequireLocsAndTags) {
				// TODO setDefaultLocation();
				// setDefaultLocation();
			}
			finishListProgress(title, mTaskList, mIsBouquetList);
		}
	}

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
			mNavName = savedInstanceState.getString("navname");
			mNavReference = savedInstanceState.getString("navreference");
			mDetailName = savedInstanceState.getString("detailname");
			mDetailReference = savedInstanceState.getString("detailreference");

			mHistory = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, "history");
			mNavItems = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, "navitems");
			mDetailItems = ExtendedHashMapHelper.restoreListFromBundle(savedInstanceState, "detailitems");

			mCurrentService = (ExtendedHashMap) savedInstanceState.getParcelable("currentService");

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

		mListTasks = new ArrayList<GetServiceListTask>();

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
	public void onDestroy() {
		if (mListTask != null)
			mListTask.cancel(true);
		super.onDestroy();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.dual_list_view, null, false);

		mEmpty = (View) v.findViewById(android.R.id.empty);

		mNavList = (ListView) v.findViewById(R.id.listView1);
		mDetailList = (ListView) v.findViewById(R.id.listView2);

		// Some may call this a Hack, but I think it a proper solution
		// On devices with resolutions other than xlarge, there is no second
		// ListView (@id/listView2).
		// So we just use the only one available and the rest will work as
		// normal with almost no additional adjustments.
		if (mDetailList == null) {
			mDetailList = mNavList;
			mDetailItems = mNavItems;
		}
		mDetailHeader = (TextView) v.findViewById(R.id.listView2Header);

		mNavList.setFastScrollEnabled(true);
		mDetailList.setFastScrollEnabled(true);
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

	/**
	 * @param key
	 * @param dfault
	 * @return
	 */
	public boolean getDataForKey(String key, boolean dfault) {
		if (mData != null) {
			Boolean b = (Boolean) mData.get(key);
			if (b != null) {
				return b.booleanValue();
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
			setDetailHeader(mDetailName);
		}
	}

	public void setDetailHeader(String title) {
		if (mDetailHeader != null)
			mDetailHeader.setText(mDetailName);
		else
			getActivity().setTitle(title);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("navname", mNavName);
		outState.putString("navreference", mNavReference);
		outState.putString("detailname", mDetailName);
		outState.putString("detailreference", mDetailReference);
		outState.putSerializable("history", mHistory);
		outState.putSerializable("navitems", mNavItems);
		outState.putSerializable("detailitems", mDetailItems);
		outState.putParcelable("currentService", mCurrentService);

		for (GetServiceListTask task : mListTasks) {
			if (task != null) {
				task.cancel(true);
				task = null;
			}
		}
		mListTasks.clear();

		super.onSaveInstanceState(outState);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		if (mListTask != null) {
			mListTask.cancel(true);
		}

		super.onPause();
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

	/**
	 * @return
	 */
	private boolean isListTaskRunning() {

		if (mListTask != null) {
			if (mListTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
				return true;
			}
		}

		return false;
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
							// there is a download Task running, the list may
							// have already been altered so we let that request
							// finish
							if (!isListTaskRunning()) {
								mNavReference = oldref;
								mNavName = oldname;
								mHistory.remove(idx);

								reloadNav();

							} else {
								showToast(getText(R.string.wait_request_finished));
							}
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
	 * @param a
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

	/**
	 * @param event
	 */
	private void setTimerById(ExtendedHashMap event) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getActionBarActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
	}

	/**
	 * @param event
	 */
	private void setTimerByEventData(ExtendedHashMap event) {
		Timer.editUsingEvent(getMultiPaneHandler(), event, this);
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
				setDetailHeader(mDetailName);
			}
			reload(mDetailReference, false);
		}
	}

	/**
	 * 
	 */
	public void reload(String ref, boolean isBouquetList) {
		mReload = false;
		if (mListTask != null) {
			mListTask.cancel(true);
		}

		ExtendedHashMap data = new ExtendedHashMap();
		data.put(Event.KEY_SERVICE_REFERENCE, String.valueOf(ref));
		data.put(Event.KEY_SERVICE_NAME, String.valueOf(ref));
		mExtras.putSerializable(sData, data);

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		if (isBouquetList || mPickMode) {
			if (ref.equals(SERVICE_REF_ROOT)) {
				loadNavRoot();
				return;
			}
			params.add(new BasicNameValuePair("sRef", ref));
		} else {
			params.add(new BasicNameValuePair("bRef", ref));
		}

		GetServiceListTask task = new GetServiceListTask();
		task.setParams(params, isBouquetList);
		enqueueListTask(task);
	}

	/**
	 * @param task
	 */
	public void enqueueListTask(GetServiceListTask task) {
		mListTasks.add(task);
		if (mListTasks.size() > 0) {
			nextListTaskPlease();
		}
	}

	@SuppressWarnings("unchecked")
	public void nextListTaskPlease() {
		if (mListTasks.size() > 0) {
			GetServiceListTask task = mListTasks.get(0);

			if (task.equals(mListTask)) {
				mListTasks.remove(0);
				if (mListTasks.size() > 0) {
					mListTask = mListTasks.get(0);
					mListTask.execute(mListTask.getParams());
				}
			} else {
				mListTask = task;
				mListTask.execute(mListTask.getParams());
			}
		}
	}

	public void loadNavRoot() {
		if (mDetailHeader == null) {
			getActionBarActivity().setTitle(getString(R.string.services));
		}

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

	/**
	 * @param title
	 * @param list
	 */
	protected void finishListProgress(String title, ArrayList<ExtendedHashMap> list, boolean isBouquetList) {
		setDetailHeader(title);
		if (mDetailHeader != null)
			finishProgress(getString(R.string.services));
		else
			getActionBarActivity().setProgressBarIndeterminateVisibility(false);
		if (isBouquetList) {
			mNavItems.clear();
			mNavItems.addAll(list);
			((BaseAdapter) mNavList.getAdapter()).notifyDataSetChanged();
		} else {
			mEmpty.setVisibility(View.GONE);
			mDetailList.setVisibility(View.VISIBLE);
			mDetailItems.clear();
			mDetailItems.addAll(list);
			((BaseAdapter) mDetailList.getAdapter()).notifyDataSetChanged();
		}
		getActionBarActivity().supportInvalidateOptionsMenu();
		nextListTaskPlease();
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
			findSimilarEvents(mCurrentService);
			break;

		case Statics.ACTION_IMDB:
			IntentFactory.queryIMDb(getActionBarActivity(), mCurrentService);
			break;
		}
	}
}
