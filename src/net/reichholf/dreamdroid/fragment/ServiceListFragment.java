/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.adapter.ServiceListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PrimitiveDialog;
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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

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
public class ServiceListFragment extends AbstractHttpFragment implements PrimitiveDialog.DialogActionListener {

	public static class ServiceActionDialog extends PrimitiveDialog {
		public static final int ACTION_CURRENT = 0xc010;
		public static final int ACTION_EPG = 0xc011;
		public static final int ACTION_ZAP = 0xc012;
		public static final int ACTION_STREAM = 0xc013;

		public static ServiceActionDialog newInstance(String serviceName) {
			ServiceActionDialog fragment = new ServiceActionDialog();
			Bundle args = new Bundle();
			args.putString("title", serviceName);
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			CharSequence[] actions = { getText(R.string.current_event), getText(R.string.browse_epg),
					getText(R.string.zap), getText(R.string.stream) };

			AlertDialog.Builder adBuilder = new AlertDialog.Builder(getActivity());
			adBuilder.setTitle(getText(R.string.pick_action));
			adBuilder.setItems(actions, new DialogInterface.OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						finishDialog(ServiceActionDialog.ACTION_CURRENT);
						break;
					case 1:
						finishDialog(ServiceActionDialog.ACTION_EPG);
						break;
					case 2:
						finishDialog(ServiceActionDialog.ACTION_ZAP);
						break;
					case 3:
						finishDialog(ServiceActionDialog.ACTION_STREAM);
						break;
					}
				}
			});

			return adBuilder.create();
		}
	}

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

	private MultiPaneHandler mMultiPaneHandler;

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

			String xml = mListRequestHandler.getList(mShc, params[0]);
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

							if (!DreamDroid.loadLocations(mShc)) {
								// TODO Add Error-Msg when loadLocations fails
							}
						}

						if (DreamDroid.getTags().size() == 0) {
							if (isCancelled())
								return false;
							publishProgress(getString(R.string.fetching_data));

							if (!DreamDroid.loadTags(mShc)) {
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
			xml = handler.getList(mShc, params[0]);

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
			String title = null;

			if (result) {
				title = mBaseTitle;
			} else {
				title = getString(R.string.get_content_error);

				if (mShc.hasError()) {
					showToast(getString(R.string.get_content_error) + "\n" + mShc.getErrorText());
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
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
		mCurrentTitle = mBaseTitle = getString(R.string.services);

		mReload = true;
		mMultiPaneHandler = (MultiPaneHandler) getSherlockActivity();

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
			mDetailReference = DreamDroid.getSharedPreferences()
					.getString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_REF, "");
			mDetailName = DreamDroid.getSharedPreferences().getString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_NAME, "");
		}

		if (mExtras != null) {
			HashMap<String, Object> map = (HashMap<String, Object>) mExtras.getSerializable("data");
			if (map != null) {
				mData = new ExtendedHashMap();
				mData.putAll(map);
			}
		} else {
			mExtras = new Bundle();
		}
		getSherlockActivity().invalidateOptionsMenu();
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

		// Some may call this a Hack, I call it a smart solution
		// On devices with resolutions other than xlarge, there is no second
		// ListView (@id/listView2).
		// So we just use the only one available and the rest will work as
		// normal with almost no additional adjustments.
		if (mDetailList == null) {
			mDetailList = mNavList;
			mDetailItems = mNavItems;
		}
		mDetailHeader = (TextView) v.findViewById(R.id.listView2Header);
		if (mDetailHeader == null) {
			// Dummy TextView for Header. Applies for non-xlarge devices
			mDetailHeader = new TextView(getSherlockActivity());
		}

		mNavList.setFastScrollEnabled(true);
		mDetailList.setFastScrollEnabled(true);

		setAdapter();
		getSherlockActivity().setTitle(mCurrentTitle);
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
			reloadDetail();
		} else {
			mDetailHeader.setText(mDetailName);
		}
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
			adapter = new SimpleAdapter(getSherlockActivity(), mNavItems, android.R.layout.simple_list_item_1,
					new String[] { Event.KEY_SERVICE_NAME }, new int[] { android.R.id.text1 });
			mNavList.setAdapter(adapter);
		}
		adapter = new ServiceListAdapter(getSherlockActivity(), R.layout.service_list_item, mDetailItems);
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
			overview.setEnabled(true);
		} else {
			overview.setEnabled(false);
		}

		MenuItem setDefault = menu.findItem(R.id.menu_default);
		String defaultReference = DreamDroid.getSharedPreferences().getString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_REF,
				null);
		setDefault.setEnabled(true);
		if (defaultReference != null) {
			if (defaultReference.equals(mDetailReference)) {
				setDefault.setEnabled(false);
			}
		}

		MenuItem reload = menu.findItem(R.id.menu_reload);
		if (!mPickMode) {
			reload.setEnabled(true);
		} else {
			reload.setEnabled(false);
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
			if (mDetailReference != null) {
				Editor editor = DreamDroid.getSharedPreferences().edit();
				editor.putString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_REF, mDetailReference);
				editor.putString(DreamDroid.PREFS_KEY_DEFAULT_BOUQUET_NAME, mDetailName);

				if (editor.commit()) {
					showToast(getText(R.string.default_bouquet_set_to) + " '" + mDetailName + "'");
				} else {
					showToast(getText(R.string.default_bouquet_not_set));
				}
			} else {
				showToast(getText(R.string.default_bouquet_not_set));
			}
			getSherlockActivity().invalidateOptionsMenu();
			return true;
		case Statics.ITEM_RELOAD:
			reloadNav();
			reloadDetail();
			return true;
		default:
			return super.onItemClicked(id);
		}
	}

	/**
	 * @param ref
	 * @return
	 */
	private boolean isBouquetList(String ref) {
		if (ref.startsWith("1:7:")) {
			return true;
		}
		return false;
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

		mMultiPaneHandler.showDetails(f, true);
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
		if (isBouquetList(ref)) {
			if (l.equals(mNavList)) {
				if (SERVICE_REF_ROOT.equals(mNavReference)) {
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
					reloadDetail();
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
				boolean instantZap = DreamDroid.getSharedPreferences().getBoolean("instant_zap", false);
				if ((instantZap && !isLong) || (!instantZap && isLong)) {
					zapTo(ref);
				} else {
					mCurrentService = item;
					mMultiPaneHandler.showDialog( ServiceActionDialog.newInstance(mCurrentService.getString(Event.KEY_SERVICE_NAME)), "service_action_dialog");
				}
			}
		}
	}

	@Override
	public Dialog onCreateDialog(int id) {
		return null;
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

		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerAddByEventIdRequestHandler(), Timer.getEventIdParams(event));
	}

	/**
	 * @param event
	 */
	private void setTimerByEventData(ExtendedHashMap event) {
		Timer.editUsingEvent(mMultiPaneHandler, event, this);
	}

	public void reloadNav() {
		reload(mNavReference, true);
	}

	public void reloadDetail() {
		if (mDetailReference != null && !"".equals(mDetailReference)) {
			// Hide ListView show empty/progress
			mEmpty.setVisibility(View.VISIBLE);
			mDetailList.setVisibility(View.GONE);
			mDetailHeader.setText(mDetailName);
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
		if (mListTasks.size() == 1) {
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
		String title = getString(R.string.services);
		getSherlockActivity().setTitle(title);

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
		getSherlockActivity().invalidateOptionsMenu();
		((BaseAdapter) mNavList.getAdapter()).notifyDataSetChanged();
	}

	@Override
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
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
		finishProgress(title);
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
		getSherlockActivity().invalidateOptionsMenu();
		nextListTaskPlease();
	}

	protected void finish(int resultCode, Intent data) {
		MultiPaneHandler mph = (MultiPaneHandler) getSherlockActivity();
		if (mph.isMultiPane()) {
			Fragment f = getTargetFragment();
			if (f != null) {
				mph.showDetails(f);
				f.onActivityResult(getTargetRequestCode(), resultCode, data);
			}
		} else {
			Activity a = getSherlockActivity();
			a.setResult(resultCode, data);
			a.finish();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.fragment.dialogs.PrimitiveDialog.
	 * DialogActionListener#onDialogAction(int)
	 */
	@Override
	public void onDialogAction(int action) {
		String ref = mCurrentService.getString(Service.KEY_REFERENCE);
		String name = mCurrentService.getString(Service.KEY_NAME);

		switch (action) {
		case ServiceActionDialog.ACTION_CURRENT:
			Bundle args = new Bundle();
			args.putParcelable("currentItem", mCurrentService);
			mMultiPaneHandler.showDialog(EpgDetailDialog.class, args, "epg_detail_dialog");
			break;
		
		case ServiceActionDialog.ACTION_EPG:
			openEpg(ref, name);
			break;
		
		case ServiceActionDialog.ACTION_ZAP:
			zapTo(ref);
			break;
		
		case ServiceActionDialog.ACTION_STREAM:
			try {
				startActivity(IntentFactory.getStreamServiceIntent(ref));
			} catch (ActivityNotFoundException e) {
				showToast(getText(R.string.missing_stream_player));
			}
			break;
		
		case EpgDetailDialog.ACTION_SET_TIMER:
			setTimerById(mCurrentService);
			break;
		
		case EpgDetailDialog.ACTION_EDIT_TIMER:
			setTimerByEventData(mCurrentService);
			break;
		
		case EpgDetailDialog.ACTION_FIND_SIMILAR:
			findSimilarEvents(mCurrentService);
			break;
		
		case EpgDetailDialog.ACTION_IMDB:
			IntentFactory.queryIMDb(getSherlockActivity(), mCurrentService);
			break;
		}
	}
}
