/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;
import java.util.Arrays;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.MultiChoiceDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieDeleteRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.OnNavigationListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

/**
 * Allows browsing recorded movies. Supports filtering by tags and locations
 * 
 * @author sreichholf
 * 
 */
public class MovieListFragment extends AbstractHttpListFragment implements ActionDialog.DialogActionListener,
		MultiChoiceDialog.MultiChoiceDialogListener {

	private String mCurrentLocation;
	private int mSelectedLocationPosition;

	private boolean mTagsChanged;
	private boolean mReloadOnSimpleResult;
	private ArrayList<String> mSelectedTags;
	private ArrayList<String> mOldTags;

	private ExtendedHashMap mMovie;
	private ProgressDialog mProgress;
	private ArrayAdapter<String> mLocationAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.movies));
		setHasOptionsMenu(true);

		mCurrentLocation = "/hdd/movie/";
		mSelectedLocationPosition = 0;

		if (savedInstanceState == null) {
			mSelectedTags = new ArrayList<String>();
			mOldTags = new ArrayList<String>();
			mReload = true;
		} else {
			mMovie = (ExtendedHashMap) savedInstanceState.getParcelable("movie");
			mSelectedTags = new ArrayList<String>(Arrays.asList(savedInstanceState.getStringArray("selectedTags")));
			mOldTags = new ArrayList<String>(Arrays.asList(savedInstanceState.getStringArray("oldTags")));
			mCurrentLocation = savedInstanceState.getString("currentLocation");
			mSelectedLocationPosition = savedInstanceState.getInt("selectedLocationPosition", 0);
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new SimpleAdapter(getActionBarActivity(), mMapList, R.layout.movie_list_item, new String[] {
				Movie.KEY_TITLE, Movie.KEY_SERVICE_NAME, Movie.KEY_FILE_SIZE_READABLE, Movie.KEY_TIME_READABLE,
				Movie.KEY_LENGTH }, new int[] { R.id.movie_title, R.id.service_name, R.id.file_size, R.id.event_start,
				R.id.event_duration });

		setListAdapter(mAdapter);
		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
				return onListItemLongClick(a, v, position, id);
			}
		});
	}

	@Override
	public void onResume() {
		setupListNavigation();
		super.onResume();
	}

	public void setupListNavigation() {
		ActionBar actionBar = getActionBarActivity().getSupportActionBar();
		if (actionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_LIST)
			return;
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		mLocationAdapter = new ArrayAdapter<String>(actionBar.getThemedContext(),
				R.layout.support_simple_spinner_dropdown_item);

		for (String location : DreamDroid.getLocations()) {
			mLocationAdapter.add(location);
		}

		int pos = mLocationAdapter.getPosition(mCurrentLocation);
		if (pos < 0)
			pos = 0;
		if (pos != mSelectedLocationPosition)
			mSelectedLocationPosition = pos;

		actionBar.setListNavigationCallbacks(mLocationAdapter, new OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {
				mSelectedLocationPosition = itemPosition;
				if (DreamDroid.getLocations().size() > itemPosition) {
					String selectedLoc = mLocationAdapter.getItem(itemPosition);
					if (!selectedLoc.equals(mCurrentLocation)) {
						mCurrentLocation = selectedLoc;
						reload();
					}
				}
				return true;
			}
		});
		actionBar.setSelectedNavigationItem(mSelectedLocationPosition);
	}

	@Override
	public void onPause() {
		getActionBarActivity().getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		super.onPause();
	}

	@Override
	protected void setDefaultLocation() {
		if (mCurrentLocation == null && DreamDroid.getLocations().size() > 0) {
			mCurrentLocation = DreamDroid.getLocations().get(0);
		}
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		onListItemClick(v, position, id, false);
	}

	/**
	 * @param a
	 * @param v
	 * @param position
	 * @param id
	 * @return
	 */
	protected boolean onListItemLongClick(AdapterView<?> a, View v, int position, long id) {
		onListItemClick(v, position, id, true);
		return true;
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.reload, menu);
		inflater.inflate(R.menu.locactions_and_tags, menu);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("movie", mMovie);

		String[] selectedTags;
		if (mSelectedTags != null) {
			selectedTags = new String[mSelectedTags.size()];
			mSelectedTags.toArray(selectedTags);
		} else {
			selectedTags = new String[0];
		}
		outState.putStringArray("selectedTags", selectedTags);

		String[] oldTags;
		if (mOldTags != null) {
			oldTags = new String[mOldTags.size()];
			mOldTags.toArray(oldTags);
		} else {
			oldTags = new String[0];
		}
		outState.putStringArray("oldTags", oldTags);
		outState.putString("currentLocation", mCurrentLocation);
		outState.putInt("selectedLocationPosition", mSelectedLocationPosition);

		super.onSaveInstanceState(outState);
	}

	/**
	 * @param id
	 *            The id of the selected menu item (<code>MENU_*</code> statics)
	 * @return
	 */
	protected boolean onItemClicked(int id) {
		switch (id) {
		case Statics.ITEM_RELOAD:
			reload();
			return true;
		case Statics.ITEM_TAGS:
			pickTags();
			return true;
		default:
			return super.onItemClicked(id);
		}
	}

	protected void pickTags() {
		CharSequence[] tags = new CharSequence[DreamDroid.getTags().size()];
		boolean[] selectedTags = new boolean[DreamDroid.getTags().size()];

		int tc = 0;
		for (String tag : DreamDroid.getTags()) {
			tags[tc] = tag;

			if (mSelectedTags.contains(tag)) {
				selectedTags[tc] = true;
			} else {
				selectedTags[tc] = false;
			}

			tc++;
		}

		mTagsChanged = false;
		mOldTags = new ArrayList<String>();
		mOldTags.addAll(mSelectedTags);

		MultiChoiceDialog f = MultiChoiceDialog.newInstance(R.string.choose_tags, tags, selectedTags, R.string.ok,
				R.string.cancel);

		getMultiPaneHandler().showDialogFragment(f, "dialog_pick_tags");
	}

	private void onListItemClick(View v, int position, long id, boolean isLong) {
		mMovie = mMapList.get(position);
		boolean isInsta = PreferenceManager.getDefaultSharedPreferences(getActionBarActivity()).getBoolean(
				"instant_zap", false);
		if ((isInsta && !isLong) || (!isInsta && isLong)) {
			zapTo(mMovie.getString(Movie.KEY_REFERENCE));
		} else {
			CharSequence[] actions = { getText(R.string.zap), getText(R.string.delete), getText(R.string.download),
					getText(R.string.stream) };

			int[] actionIds = { Statics.ACTION_ZAP, Statics.ACTION_DELETE, Statics.ACTION_DOWNLOAD,
					Statics.ACTION_STREAM };

			getMultiPaneHandler().showDialogFragment(
					SimpleChoiceDialog.newInstance(getString(R.string.pick_action), actions, actionIds),
					"dialog_movie_selected");
		}
	}

	/**
	 * Delete the selected movie
	 */
	private void deleteMovie() {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getActionBarActivity(), "", getText(R.string.deleting), true);
		mReloadOnSimpleResult = true;
		execSimpleResultTask(new MovieDeleteRequestHandler(), Movie.getDeleteParams(mMovie));
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		if (mReloadOnSimpleResult) {
			if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
				reload();
				mReloadOnSimpleResult = false;
			}
		}
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		if (mCurrentLocation != null) {
			params.add(new BasicNameValuePair("dirname", mCurrentLocation));
		}

		if (mSelectedTags.size() > 0) {
			String tags = Tag.implodeTags(mSelectedTags);
			params.add(new BasicNameValuePair("tag", tags));
		}

		return params;
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getActionBarActivity(), new MovieListRequestHandler(), true, args);
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
			LoaderResult<ArrayList<ExtendedHashMap>> result) {
		//when popping fromt he backstack (e.g. after epg search) onStart will restore the loader which will in return call onLoadfinished
		//because this in done twice (in onStart and in onResumed and we are not ready to handle this before onResume, we ignore any onLoadFinished
		//that happens while we are not in a Resumed state
		if (!isResumed())
			return;
		super.onLoadFinished(loader, result);

		mLocationAdapter.clear();
		for (String location : DreamDroid.getLocations()) {
			mLocationAdapter.add(location);
		}

		mSelectedLocationPosition = mLocationAdapter.getPosition(mCurrentLocation);
		getActionBarActivity().getSupportActionBar().setSelectedNavigationItem(mSelectedLocationPosition);
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		switch (action) {
		case Statics.ACTION_ZAP:
			zapTo(mMovie.getString(Movie.KEY_REFERENCE));
			break;

		case Statics.ACTION_DELETE:
			getMultiPaneHandler().showDialogFragment(
					PositiveNegativeDialog.newInstance(mMovie.getString(Movie.KEY_TITLE), R.string.delete_confirm,
							android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
							Statics.ACTION_NONE), "dialog_delete_movie_confirm");
			break;

		case Statics.ACTION_DELETE_CONFIRMED:
			deleteMovie();
			break;

		case Statics.ACTION_DOWNLOAD:
			ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("file", mMovie.getString(Movie.KEY_FILE_NAME)));
			String url = getHttpClient().buildUrl(URIStore.FILE, params);

			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			startActivity(intent);
			break;

		case Statics.ACTION_STREAM:
			try {
				startActivity(IntentFactory.getStreamFileIntent(mMovie.getString(Movie.KEY_FILE_NAME),
						mMovie.getString(Movie.KEY_TITLE)));
			} catch (ActivityNotFoundException e) {
				showToast(getText(R.string.missing_stream_player));
			}
			break;
		default:
			return;
		}
	}

	@Override
	public void onMultiChoiceDialogChange(String dialogTag, DialogInterface dialog, int which, boolean isChecked) {
		String tag = DreamDroid.getTags().get(which);
		mTagsChanged = true;
		if (isChecked) {
			if (!mSelectedTags.contains(tag)) {
				mSelectedTags.add(tag);
			}
		} else {
			int idx = mSelectedTags.indexOf(tag);
			if (idx >= 0) {
				mSelectedTags.remove(idx);
			}
		}
	}

	@Override
	public void onMultiChoiceDialogFinish(String dialogTag, int result) {
		if ("dialog_pick_tags".equals(dialogTag)) {
			if (result == Activity.RESULT_CANCELED) {
				mSelectedTags.clear();
				mSelectedTags.addAll(mOldTags);
			} else if (result == Activity.RESULT_OK) {
				if (mTagsChanged) {
					reload();
				}
			}
		}

	}
}
