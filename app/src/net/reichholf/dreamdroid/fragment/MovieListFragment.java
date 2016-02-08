/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

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
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.SimpleTextAdapter;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.MovieDetailDialog;
import net.reichholf.dreamdroid.fragment.dialogs.MultiChoiceDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
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

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Allows browsing recorded movies. Supports filtering by tags and locations
 *
 * @author sreichholf
 */
public class MovieListFragment extends BaseHttpRecyclerFragment implements ActionDialog.DialogActionListener,
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
		mEnableReload = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.movies));

		mCurrentLocation = "/hdd/movie/";
		mSelectedLocationPosition = 0;

		if (savedInstanceState == null) {
			mSelectedTags = new ArrayList<>();
			mOldTags = new ArrayList<>();
			mReload = true;
		} else {
			mMovie = savedInstanceState.getParcelable("movie");
			mSelectedTags = new ArrayList<>(Arrays.asList(savedInstanceState.getStringArray("selectedTags")));
			mOldTags = new ArrayList<>(Arrays.asList(savedInstanceState.getStringArray("oldTags")));
			mCurrentLocation = savedInstanceState.getString("currentLocation");
			mSelectedLocationPosition = savedInstanceState.getInt("selectedLocationPosition", 0);
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new SimpleTextAdapter(mMapList, R.layout.movie_list_item, new String[]{
				Movie.KEY_TITLE, Movie.KEY_SERVICE_NAME, Movie.KEY_FILE_SIZE_READABLE, Movie.KEY_TIME_READABLE,
				Movie.KEY_LENGTH}, new int[]{R.id.movie_title, R.id.service_name, R.id.file_size, R.id.event_start,
				R.id.event_duration});
		getRecyclerView().setAdapter(mAdapter);
	}

	@Override
	public void onResume() {
		setupListNavigation();
		super.onResume();
	}

	public void setupListNavigation() {
		ActionBar actionBar = getAppCompatActivity().getSupportActionBar();
		if (actionBar.getNavigationMode() == ActionBar.NAVIGATION_MODE_LIST)
			return;
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		mLocationAdapter = new ArrayAdapter<>(actionBar.getThemedContext(),
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
		getAppCompatActivity().getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		super.onPause();
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
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

	@Override
	protected boolean onItemSelected(int id) {
		switch (id) {
			case Statics.ITEM_TAGS:
				pickTags();
				return true;
			default:
				return super.onItemSelected(id);
		}
	}

	protected void pickTags() {
		CharSequence[] tags = new CharSequence[DreamDroid.getTags().size()];
		boolean[] selectedTags = new boolean[DreamDroid.getTags().size()];

		int tc = 0;
		for (String tag : DreamDroid.getTags()) {
			tags[tc] = tag;

			selectedTags[tc] = mSelectedTags.contains(tag);

			tc++;
		}

		mTagsChanged = false;
		mOldTags = new ArrayList<>();
		mOldTags.addAll(mSelectedTags);

		MultiChoiceDialog f = MultiChoiceDialog.newInstance(R.string.choose_tags, tags, selectedTags, R.string.ok,
				R.string.cancel);

		getMultiPaneHandler().showDialogFragment(f, "dialog_pick_tags");
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		onMovieItemClick(view, position, false);
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, View view, int position, long id) {
		onMovieItemClick(view, position, true);
		return true;
	}

	private void onMovieItemClick(View view, int position, boolean isLong) {
	mMovie = mMapList.get(position);
		boolean isInsta = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity()).getBoolean(
				"instant_zap", false);
		if ((isInsta && !isLong) || (!isInsta && isLong)) {
			zapTo(mMovie.getString(Movie.KEY_REFERENCE));
		} else {
			showPopupMenu(view);
		}
	}

	public void showPopupMenu(View v) {
		PopupMenu menu = new PopupMenu(getAppCompatActivity(), v);
		menu.getMenuInflater().inflate(R.menu.popup_movielist, menu.getMenu());
		menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem menuItem) {
				return onMovieAction(menuItem.getItemId());
			}
		});
		menu.show();
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

		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.deleting), true);
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
		ArrayList<NameValuePair> params = new ArrayList<>();
		if (mCurrentLocation != null) {
			params.add(new NameValuePair("dirname", mCurrentLocation));
		}

		if (mSelectedTags.size() > 0) {
			String tags = Tag.implodeTags(mSelectedTags);
			params.add(new NameValuePair("tag", tags));
		}

		return params;
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		return new AsyncListLoader(getAppCompatActivity(), new MovieListRequestHandler(), true, args);
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
		getAppCompatActivity().getSupportActionBar().setSelectedNavigationItem(mSelectedLocationPosition);
	}

	public void onDialogAction(int action, Object details, String dialogTag) {
		onMovieAction(action);
	}

	public boolean onMovieAction(int action) {
		switch (action) {
			case R.id.menu_info: {
				if(mMovie.getString(Movie.KEY_DESCRIPTION_EXTENDED) == null){
					showToast(getString(R.string.no_epg_available));
					break;
				}
				getMultiPaneHandler().showDialogFragment(MovieDetailDialog.newInstance(mMovie), "movie_detail_dialog");
				break;
			}

			case R.id.menu_zap:
				zapTo(mMovie.getString(Movie.KEY_REFERENCE));
				break;

			case R.id.menu_delete:
				getMultiPaneHandler().showDialogFragment(
						PositiveNegativeDialog.newInstance(mMovie.getString(Movie.KEY_TITLE), R.string.delete_confirm,
								android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
								Statics.ACTION_NONE), "dialog_delete_movie_confirm");
				break;

			case Statics.ACTION_DELETE_CONFIRMED:
				deleteMovie();
				break;

			case R.id.menu_download:
				ArrayList<NameValuePair> params = new ArrayList<>();
				params.add(new NameValuePair("file", mMovie.getString(Movie.KEY_FILE_NAME)));
				String url = getHttpClient().buildUrl(URIStore.FILE, params);

				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(intent);
				break;

			case R.id.menu_stream:
				try {
					startActivity(IntentFactory.getStreamFileIntent(mMovie.getString(Movie.KEY_FILE_NAME),
							mMovie.getString(Movie.KEY_TITLE)));
				} catch (ActivityNotFoundException e) {
					showToast(getText(R.string.missing_stream_player));
				}
				break;
			default:
				return false;
		}
		return true;
	}

	@Override
	public void onMultiChoiceDialogSelection(String dialogTag, DialogInterface dialog, Integer[] selected) {
		ArrayList<String> tags = DreamDroid.getTags();
		ArrayList<String> selectedTags = new ArrayList<>();
		for (Integer which : selected) {
			selectedTags.add(tags.get(which));
		}
		mTagsChanged = !selectedTags.equals(mSelectedTags);
		mSelectedTags = selectedTags;
	}

	@Override
	public void onMultiChoiceDialogFinish(String dialogTag, int result) {
		if ("dialog_pick_tags".equals(dialogTag) && mTagsChanged)
			reload();
	}
}
