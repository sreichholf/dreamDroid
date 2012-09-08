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

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.OnNavigationListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;

/**
 * Allows browsing recorded movies. Supports filtering by tags and locations
 * 
 * @author sreichholf
 * 
 */
public class MovieListFragment extends AbstractHttpListFragment {
	private String mCurrentLocation;

	private boolean mTagsChanged;
	private boolean mReloadOnSimpleResult;
	private ArrayList<String> mSelectedTags;
	private ArrayList<String> mOldTags;

	private ExtendedHashMap mMovie;
	private ProgressDialog mProgress;
	private ArrayAdapter<String> mLocationAdapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mCurrentTitle = mBaseTitle = "";
		mCurrentLocation = "/hdd/movie/";
		setHasOptionsMenu(true);

		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mAdapter = new SimpleAdapter(getSherlockActivity(), mMapList, R.layout.movie_list_item, new String[] {
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

		if (savedInstanceState == null) {
			mSelectedTags = new ArrayList<String>();
			mOldTags = new ArrayList<String>();
			reload();
		} else {
			mMovie = (ExtendedHashMap) savedInstanceState.getParcelable("movie");
			mSelectedTags = new ArrayList<String>(Arrays.asList(savedInstanceState.getStringArray("selectedTags")));
			mOldTags = new ArrayList<String>(Arrays.asList(savedInstanceState.getStringArray("oldTags")));
		}
	}
	
	@Override
	public void onResume(){
		ActionBar actionBar = getSherlockActivity().getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		mLocationAdapter = new ArrayAdapter<String>(getSherlockActivity(), android.R.layout.simple_list_item_1);
		mLocationAdapter.add(mCurrentLocation);
		actionBar.setListNavigationCallbacks(mLocationAdapter, new OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {			
				if(DreamDroid.getLocations().size() > itemPosition){
					ActionBar actionBar = getSherlockActivity().getSupportActionBar();
					int position = actionBar.getSelectedNavigationIndex();
					String selectedLoc = mLocationAdapter.getItem(position); 
					if (!selectedLoc.equals(mCurrentLocation)) {
						mCurrentLocation = selectedLoc;
						reload();
					}
				}
				return true;
			}
		});
		super.onResume();
	}
	
	@Override
	public void onPause(){
		getSherlockActivity().getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
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
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
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
			getSherlockActivity().showDialog(Statics.DIALOG_PICK_TAGS_ID);
			return true;
		default:
			return super.onItemClicked(id);
		}
	}

	@Override
	public Dialog onCreateDialog(int id) {
		final Dialog dialog;
		AlertDialog.Builder builder;

		switch (id) {
		case (Statics.DIALOG_MOVIE_SELECTED_ID):
			CharSequence[] actions = { getText(R.string.zap), getText(R.string.delete), getText(R.string.download),
					getText(R.string.stream) };

			builder = new AlertDialog.Builder(getSherlockActivity());
			builder.setTitle(getText(R.string.pick_action));
			builder.setItems(actions, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						zapTo(mMovie.getString(Movie.KEY_REFERENCE));
						break;
					case 1:
						getSherlockActivity().showDialog(Statics.DIALOG_DELETE_MOVIE_CONFIRM_ID);
						break;
					case 2:
						ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
						params.add(new BasicNameValuePair("file", mMovie.getString(Movie.KEY_FILE_NAME)));
						String url = mShc.buildUrl(URIStore.FILE, params);

						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
						startActivity(intent);
						break;
					case 3:
						try {
							startActivity(IntentFactory.getStreamFileIntent(mMovie.getString(Movie.KEY_FILE_NAME)));
						} catch (ActivityNotFoundException e) {
							showToast(getText(R.string.missing_stream_player));
						}
						break;
					default:
						return;
					}
				}
			});
			dialog = builder.create();
			break;
		case (Statics.DIALOG_DELETE_MOVIE_CONFIRM_ID):
			builder = new AlertDialog.Builder(getSherlockActivity());
			builder.setTitle(mMovie.getString(Movie.KEY_TITLE)).setMessage(getText(R.string.delete_confirm))
					.setCancelable(false)
					.setPositiveButton(getText(android.R.string.yes), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							deleteMovie();
							dialog.dismiss();
						}
					}).setNegativeButton(getText(android.R.string.no), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
							getSherlockActivity().removeDialog(Statics.DIALOG_DELETE_MOVIE_CONFIRM_ID);
						}
					});
			dialog = builder.create();
			break;

		case (Statics.DIALOG_PICK_TAGS_ID):
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

			builder = new AlertDialog.Builder(getSherlockActivity());
			builder.setTitle(getText(R.string.choose_tags));

			builder.setMultiChoiceItems(tags, selectedTags, new OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
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

			});

			builder.setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (mTagsChanged) {
						reload();
					}
					dialog.dismiss();
					getSherlockActivity().removeDialog(Statics.DIALOG_PICK_TAGS_ID);
				}

			});

			builder.setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mSelectedTags.clear();
					mSelectedTags.addAll(mOldTags);
					dialog.dismiss();
					getSherlockActivity().removeDialog(Statics.DIALOG_PICK_TAGS_ID);
				}

			});

			dialog = builder.create();
			break;
		default:
			dialog = null;
		}

		return dialog;
	}

	@Override
	protected void finishListProgress(String title, ArrayList<ExtendedHashMap> list) {
		super.finishListProgress(title, list);

		if (mCurrentLocation == null) {
			setDefaultLocation();
		}
	}

	/**
	 * @param v
	 * @param position
	 * @param id
	 * @param isLong
	 */
	private void onListItemClick(View v, int position, long id, boolean isLong) {
		mMovie = mMapList.get(position);
		boolean isInsta = DreamDroid.getSharedPreferences().getBoolean("instant_zap", false);
		if ((isInsta && !isLong) || (!isInsta && isLong)) {
			zapTo(mMovie.getString(Movie.KEY_REFERENCE));
		} else {
			getSherlockActivity().showDialog(Statics.DIALOG_MOVIE_SELECTED_ID);
		}
	}

	/**
	 * Delete the selected movie
	 */
	private void deleteMovie() {
		getSherlockActivity().removeDialog(Statics.DIALOG_DELETE_MOVIE_CONFIRM_ID);
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getSherlockActivity(), "", getText(R.string.deleting), true);
		mReloadOnSimpleResult = true;
		execSimpleResultTask(new MovieDeleteRequestHandler(), Movie.getDeleteParams(mMovie));
	}

	@Override
	protected void onSimpleResult(boolean success, ExtendedHashMap result) {
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
	protected ArrayList<NameValuePair> getHttpParams() {
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
	public Loader<ArrayList<ExtendedHashMap>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getSherlockActivity(), new MovieListRequestHandler(), true, args);
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<ArrayList<ExtendedHashMap>> loader, ArrayList<ExtendedHashMap> list) {
		super.onLoadFinished(loader, list);

		mLocationAdapter.clear();
		for (String location : DreamDroid.getLocations()) {
			mLocationAdapter.add(location);
		}
		
		getSherlockActivity().getSupportActionBar().setSelectedNavigationItem(
				mLocationAdapter.getPosition(mCurrentLocation));
	}
}
