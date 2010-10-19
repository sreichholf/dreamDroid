/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import java.util.ArrayList;

import net.reichholf.dreamdroid.abstivities.AbstractHttpListActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
import net.reichholf.dreamdroid.helpers.enigma2.Service;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import net.reichholf.dreamdroid.helpers.enigma2.Timer;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * Allows browsing recorded movies. Supports filtering by tags and locations
 * 
 * @author sreichholf
 * 
 */
public class MovieListActivity extends AbstractHttpListActivity {
	public static final int MENU_LOCATIONS = 0;
	public static final int MENU_TAGS = 1;
	public static final int MENU_RELOAD = 2;

	public static final int DIALOG_PICK_LOCATION_ID = 0;
	public static final int DIALOG_PICK_TAGS_ID = 1;
	public static final int DIALOG_DELETE_MOVIE_CONFIRM_ID = 2;

	private String mCurrentLocation;

	private boolean mTagsChanged;
	private ArrayList<String> mSelectedTags;
	private ArrayList<String> mOldTags;

	private ExtendedHashMap mMovie;
	private ProgressDialog mDeleteProgress;
	private GetMovieListTask mListTask;
	private DeleteMovieTask mDeleteTask;

	/**
	 * <code>AsyncTask</code> to get the list of recorded movies
	 * 
	 * @author sreichholf
	 * 
	 */
	private class GetMovieListTask extends AsyncTask<ArrayList<NameValuePair>, String, Boolean> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			publishProgress(getText(R.string.app_name) + "::" + getText(R.string.movies) + " - "
					+ getText(R.string.fetching_data));

			mMapList.clear();
			String xml = Movie.getList(mShc, params);

			if (xml != null) {
				publishProgress(getText(R.string.app_name) + "::" + getText(R.string.movies) + " - "
						+ getText(R.string.parsing));

				if (Movie.parseList(xml, mMapList)) {
					if (DreamDroid.LOCATIONS.size() == 0) {
						publishProgress(getText(R.string.app_name) + "::" + getText(R.string.movies) + " - "
								+ getText(R.string.locations) + " - " + getText(R.string.fetching_data));

						if (!DreamDroid.loadLocations(mShc)) {
							// TODO Add Error-Msg when loadLocations fails
						}

						if (mCurrentLocation == null && DreamDroid.LOCATIONS.size() > 0) {
							mCurrentLocation = DreamDroid.LOCATIONS.get(0);
						}
					}

					if (DreamDroid.TAGS.size() == 0) {
						publishProgress(getText(R.string.app_name) + "::" + getText(R.string.movies) + " - "
								+ getText(R.string.tags) + " - " + getText(R.string.fetching_data));

						if (!DreamDroid.loadTags(mShc)) {
							// TODO Add Error-Msg when loadTags fails
						}
					}

					return true;
				}
			}
			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {
			setTitle(progress[0]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			String title = null;
			mAdapter.notifyDataSetChanged();

			if (result) {
				title = getText(R.string.app_name) + "::" + getText(R.string.movies);

				if (mMapList.size() == 0) {
					showDialog(DIALOG_EMPTY_LIST_ID);
				}
			} else {
				title = getText(R.string.app_name) + "::" + getText(R.string.movies) + " - "
						+ getText(R.string.get_content_error);

				if (mShc.hasError()) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}

			setTitle(title);
		}
	}

	/**
	 * <code>AsyncTask</code> to delete a movie using its service reference
	 * Calls <code>onMovieDeleted</code> when finished.
	 * 
	 * @author sre
	 * 
	 */
	private class DeleteMovieTask extends AsyncTask<String, String, Boolean> {
		private ExtendedHashMap mResult;
		private boolean mHttpError;

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Boolean doInBackground(String... params) {
			String xml = Movie.delete(mShc, mMovie);
			mHttpError = false;
			if (xml != null) {
				ExtendedHashMap result = Timer.parseSimpleResult(xml);

				String stateText = result.getString("statetext");

				if (stateText != null) {
					mResult = result;
					return true;
				}
			} else {
				mHttpError = true;
			}

			return false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(String... progress) {

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		protected void onPostExecute(Boolean result) {
			if (!result) {
				mResult = new ExtendedHashMap();
				if (mHttpError) {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
				}
			}
			onMovieDeleted(mResult);
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

		mAdapter = new SimpleAdapter(this, mMapList, R.layout.movie_list_item, new String[] { Movie.TITLE,
				Movie.SERVICE_NAME, Movie.FILE_SIZE_READABLE, Movie.TIME_READABLE, Movie.LENGTH }, new int[] {
				R.id.movie_title, R.id.service_name, R.id.file_size, R.id.event_start, R.id.event_duration });

		setListAdapter(mAdapter);
		mSelectedTags = new ArrayList<String>();

		reload();
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
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mMovie = mMapList.get(position);

		CharSequence[] actions = { getText(R.string.zap), getText(R.string.delete) };

		AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
		adBuilder.setTitle(getText(R.string.pick_action));
		adBuilder.setItems(actions, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case 0:
					zapTo(mMovie.getString(Movie.REFERENCE));
					break;
				case 1:
					showDialog(DIALOG_DELETE_MOVIE_CONFIRM_ID);
					break;
				default:
					return;
				}
			}
		});

		adBuilder.show();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, MENU_RELOAD, 0, getText(R.string.reload)).setIcon(android.R.drawable.ic_menu_rotate);
		menu.add(0, MENU_LOCATIONS, 1, getText(R.string.locations)).setIcon(R.drawable.ic_menu_locations);
		menu.add(0, MENU_TAGS, 1, getText(R.string.tags)).setIcon(R.drawable.ic_menu_tags);

		return true;
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
			showDialog(DIALOG_PICK_LOCATION_ID);
			return true;
		case MENU_TAGS:
			showDialog(DIALOG_PICK_TAGS_ID);
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
	protected Dialog onCreateDialog(int id) {
		final Dialog dialog;
		AlertDialog.Builder builder;

		switch (id) {
		case (DIALOG_DELETE_MOVIE_CONFIRM_ID):
			builder = new AlertDialog.Builder(this);
			builder.setTitle(mMovie.getString(Movie.TITLE)).setMessage(getText(R.string.delete_confirm))
					.setCancelable(false)
					.setPositiveButton(getText(android.R.string.yes), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							deleteMovie();
							dialog.dismiss();
						}
					}).setNegativeButton(getText(android.R.string.no), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.dismiss();
							removeDialog(DIALOG_PICK_LOCATION_ID);
						}
					});
			dialog = builder.create();
			break;

		case (DIALOG_PICK_LOCATION_ID):
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

			builder = new AlertDialog.Builder(this);
			builder.setTitle(getText(R.string.choose_location));

			builder.setSingleChoiceItems(locations, selectedIndex, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String selectedLoc = DreamDroid.LOCATIONS.get(which);
					if (!selectedLoc.equals(mCurrentLocation)) {
						mCurrentLocation = DreamDroid.LOCATIONS.get(which);
						reload();
					}
					dialog.dismiss();
				}
			});

			dialog = builder.create();
			break;

		case (DIALOG_PICK_TAGS_ID):
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

			builder = new AlertDialog.Builder(this);
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
					removeDialog(DIALOG_PICK_TAGS_ID);
				}

			});

			builder.setNegativeButton(android.R.string.cancel, new Dialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mSelectedTags.clear();
					mSelectedTags.addAll(mOldTags);
					dialog.dismiss();
					removeDialog(DIALOG_PICK_TAGS_ID);
				}

			});

			dialog = builder.create();
			break;
		default:
			dialog = super.onCreateDialog(id);
		}

		return dialog;
	}

	/**
	 * @param ref
	 *            The service reference to zap to
	 */
	public void zapTo(String ref) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", ref));
		String xml = Service.zap(mShc, params);

		ExtendedHashMap result = Service.parseSimpleResult(xml);

		String resulttext = (String) getText(R.string.get_content_error);
		if (result != null) {
			resulttext = result.getString(SimpleResult.STATE_TEXT);
		}

		Toast toast = Toast.makeText(getApplicationContext(), resulttext, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * Delete the selected movie
	 */
	private void deleteMovie() {
		if (mDeleteTask != null) {
			mDeleteTask.cancel(true);
			if (mDeleteProgress != null) {
				if (mDeleteProgress.isShowing()) {
					mDeleteProgress.dismiss();
				}
			}
		}

		mDeleteProgress = ProgressDialog.show(this, "", getText(R.string.deleting), true);

		mDeleteTask = new DeleteMovieTask();
		mDeleteTask.execute("");
	}

	/**
	 * @param result
	 *            The result of deleting a specific movie as
	 *            <helpers.enigma2.SimleResult>
	 */
	private void onMovieDeleted(ExtendedHashMap result) {
		mDeleteProgress.dismiss();

		String toastText = (String) getText(R.string.get_content_error);

		String stateText = result.getString("statetext");

		if (stateText != null && !"".equals(stateText)) {
			toastText = stateText;
		}

		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();

		if ("True".equals(result.getString("state"))) {
			reload();
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
