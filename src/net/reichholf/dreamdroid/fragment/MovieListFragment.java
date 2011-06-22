/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.helpers.IdHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieDeleteRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemLongClickListener;

/**
 * Allows browsing recorded movies. Supports filtering by tags and locations
 * 
 * @author sreichholf
 * 
 */
@SuppressWarnings("unused")
public class MovieListFragment extends AbstractHttpListFragment {
	public static final int MENU_LOCATIONS = 0;
	public static final int MENU_TAGS = 1;
	public static final int MENU_RELOAD = 2;

	private String mCurrentLocation;

	private boolean mTagsChanged;
	private boolean mReloadOnSimpleResult;
	private ArrayList<String> mSelectedTags;
	private ArrayList<String> mOldTags;

	private ExtendedHashMap mMovie;
	private ProgressDialog mProgress;
	private GetMovieListTask mListTask;

	/**
	 * <code>AsyncTask</code> to get the list of recorded movies
	 * 
	 * @author sreichholf
	 * 
	 */
	private class GetMovieListTask extends AsyncListUpdateTask {
		public GetMovieListTask() {
			super(getString(R.string.movies), new MovieListRequestHandler(), true);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.activities.AbstractHttpListActivity#onCreate
	 * (android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}
	
	@Override 
	public void onActivityCreated(Bundle savedInstanceState){
		super.onActivityCreated(savedInstanceState);
		mAdapter = new SimpleAdapter(getActivity(), mMapList, R.layout.movie_list_item, new String[] { Movie.KEY_TITLE,
			Movie.KEY_SERVICE_NAME, Movie.KEY_FILE_SIZE_READABLE, Movie.KEY_TIME_READABLE, Movie.KEY_LENGTH }, new int[] {
			R.id.movie_title, R.id.service_name, R.id.file_size, R.id.event_start, R.id.event_duration });

		setListAdapter(mAdapter);
		mSelectedTags = new ArrayList<String>();
		
		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
				return onListItemLongClick(a, v, position, id);
			}
		});
		
		reload();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#
	 * setDefaultLocation()
	 */
	@Override
	protected void setDefaultLocation() {
		if (mCurrentLocation == null && DreamDroid.LOCATIONS.size() > 0) {
			mCurrentLocation = DreamDroid.LOCATIONS.get(0);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		if (mListTask != null) {
			mListTask.cancel(true);
		}

		super.onPause();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
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
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);

		menu.add(0, MENU_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, MENU_LOCATIONS, 1, getText(R.string.locations)).setIcon(R.drawable.ic_menu_locations);
		menu.add(0, MENU_TAGS, 1, getText(R.string.tags)).setIcon(R.drawable.ic_menu_tags);
	}

	/**
	 * @param id
	 *            The id of the selected menu item (<code>MENU_*</code> statics)
	 * @return
	 */
	protected boolean onItemClicked(int id) {
		switch (id) {
		case MENU_RELOAD:
			reload();
			return true;
		case MENU_LOCATIONS:
			getActivity().showDialog(IdHelper.DIALOG_PICK_LOCATION_ID);
			return true;
		case MENU_TAGS:
			getActivity().showDialog(IdHelper.DIALOG_PICK_TAGS_ID);
			return true;
		default:
			return super.onItemClicked(id);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	public Dialog onCreateDialog(int id) {
		final Dialog dialog;
		AlertDialog.Builder builder;

		switch (id) {
		case (IdHelper.DIALOG_DELETE_MOVIE_CONFIRM_ID):
			builder = new AlertDialog.Builder(getActivity());
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
							getActivity().removeDialog(IdHelper.DIALOG_DELETE_MOVIE_CONFIRM_ID);
						}
					});
			dialog = builder.create();
			break;

		case (IdHelper.DIALOG_PICK_LOCATION_ID):
			CharSequence[] locations = new CharSequence[DreamDroid.LOCATIONS.size()];

			int selectedIndex = 0;
			int lc = 0;
			for (String location : DreamDroid.LOCATIONS) {
				locations[lc] = location;
				if (location.equals(mCurrentLocation)) {
					selectedIndex = lc;
				}
				lc++;
			}

			builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(getText(R.string.choose_location));

			builder.setSingleChoiceItems(locations, selectedIndex, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String selectedLoc = DreamDroid.LOCATIONS.get(which);
					if (!selectedLoc.equals(mCurrentLocation)) {
						mCurrentLocation = selectedLoc;
						reload();
					}
					dialog.dismiss();
					getActivity().removeDialog(IdHelper.DIALOG_PICK_LOCATION_ID);
				}
			});

			dialog = builder.create();
			break;

		case (IdHelper.DIALOG_PICK_TAGS_ID):
			CharSequence[] tags = new CharSequence[DreamDroid.TAGS.size()];
			boolean[] selectedTags = new boolean[DreamDroid.TAGS.size()];

			int tc = 0;
			for (String tag : DreamDroid.TAGS) {
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

			builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(getText(R.string.choose_tags));

			builder.setMultiChoiceItems(tags, selectedTags, new OnMultiChoiceClickListener() {
				/*
				 * (non-Javadoc)
				 * 
				 * @see android.content.DialogInterface.
				 * OnMultiChoiceClickListener
				 * #onClick(android.content.DialogInterface, int, boolean)
				 */
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					String tag = DreamDroid.TAGS.get(which);
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
					getActivity().removeDialog(IdHelper.DIALOG_PICK_TAGS_ID);
				}

			});

			builder.setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mSelectedTags.clear();
					mSelectedTags.addAll(mOldTags);
					dialog.dismiss();
					getActivity().removeDialog(IdHelper.DIALOG_PICK_TAGS_ID);
				}

			});

			dialog = builder.create();
			break;
		default:
			dialog = null;
		}

		return dialog;
	}
	
	/* (non-Javadoc)
	 * @see net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#finishListProgress(java.lang.String, java.util.ArrayList)
	 */
	@Override
	protected void finishListProgress(String title, ArrayList<ExtendedHashMap> list) {
		super.finishListProgress(title, list);
		
		if(mCurrentLocation == null){
			setDefaultLocation();
		}		
	}
	
	/**
	 * @param v
	 * @param position
	 * @param id
	 * @param isLong
	 */
	private void onListItemClick(View v, int position, long id, boolean isLong){
		mMovie = mMapList.get(position);
		boolean isInsta = DreamDroid.SP.getBoolean("instant_zap", false);
		if( ( isInsta && !isLong ) || (!isInsta && isLong ) ){
			zapTo(mMovie.getString(Movie.KEY_REFERENCE));
		} else {
		
			CharSequence[] actions = { getText(R.string.zap), getText(R.string.delete), getText(R.string.download),
					getText(R.string.stream) };
	
			AlertDialog.Builder adBuilder = new AlertDialog.Builder(getActivity());
			adBuilder.setTitle(getText(R.string.pick_action));
			adBuilder.setItems(actions, new DialogInterface.OnClickListener() {
	
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						zapTo(mMovie.getString(Movie.KEY_REFERENCE));
						break;
					case 1:
						getActivity().showDialog(IdHelper.DIALOG_DELETE_MOVIE_CONFIRM_ID);
						break;
					case 2:
						ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
						params.add(new BasicNameValuePair("file", mMovie.getString(Movie.KEY_FILE_NAME)));
						String url = mShc.buildUrl(URIStore.FILE, params);
	
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
						startActivity(intent);
						break;
					case 3:
						try{
							startActivity( IntentFactory.getStreamFileIntent(mMovie.getString(Movie.KEY_FILE_NAME)) );
						} catch(ActivityNotFoundException e){
							showToast(getText(R.string.missing_stream_player));
						}
						break;
					default:
						return;
					}
				}
			});
	
			adBuilder.show();
		}
	}
	
	
	/**
	 * Delete the selected movie
	 */
	private void deleteMovie() {
		getActivity().removeDialog(IdHelper.DIALOG_DELETE_MOVIE_CONFIRM_ID);
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getActivity(), "", getText(R.string.deleting), true);
		mReloadOnSimpleResult = true;
		execSimpleResultTask(new MovieDeleteRequestHandler(), Movie.getDeleteParams(mMovie));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity#onSimpleResult
	 * (boolean, net.reichholf.dreamdroid.helpers.ExtendedHashMap)
	 */
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

	/**
	 * Reload the list of movies
	 */
	@SuppressWarnings("unchecked")
	private void reload() {
		mTagsChanged = false;

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		if (mCurrentLocation != null) {
			params.add(new BasicNameValuePair("dirname", mCurrentLocation));
		}

		if (mSelectedTags.size() > 0) {
			String tags = Tag.implodeTags(mSelectedTags);
			params.add(new BasicNameValuePair("tag", tags));
		}

		if (mListTask != null) {
			mListTask.cancel(true);
		}

		mListTask = new GetMovieListTask();
		mListTask.execute(params);
	}
}
