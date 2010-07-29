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
import net.reichholf.dreamdroid.helpers.enigma2.Timer;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import net.reichholf.dreamdroid.R;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

/**
 * @author sreichholf
 * 
 */
public class MovieListActivity extends AbstractHttpListActivity {
	private AsyncTask<ArrayList<NameValuePair>, String, Boolean> currentTask;
	private ExtendedHashMap mMovie;
	private ProgressDialog mDeleteProgress;
	private AsyncTask<String, String, Boolean> mDeleteTask;

	/**
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

			mList.clear();
			String xml = Movie.getList(mShc, params);

			if (xml != null) {
				publishProgress(getText(R.string.app_name) + "::" + getText(R.string.movies) + " - "
						+ getText(R.string.parsing));

				if (Movie.parseList(xml, mList)) {
					return true;
				} else {
					showToast(getText(R.string.get_content_error) + "\n" + mShc.getErrorText());
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

			if (result) {
				title = getText(R.string.app_name) + "::" + getText(R.string.movies);
				mAdapter.notifyDataSetChanged();
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

		mAdapter = new SimpleAdapter(this, mList, R.layout.movie_list_item, new String[] { Movie.TITLE,
				Movie.SERVICE_NAME, Movie.FILE_SIZE_READABLE, Movie.TIME_READABLE, Movie.LENGTH }, new int[] {
				R.id.movie_title, R.id.service_name, R.id.file_size, R.id.event_start, R.id.event_duration });
		setListAdapter(mAdapter);
		reload();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		if (currentTask != null) {
			currentTask.cancel(true);
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
		mMovie = mList.get(position);

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
					deleteMovieConfirm();
					break;				
				default:
					return;
				}
			}
		});

		adBuilder.show();
	}

	/**
	 * @param ref
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
	 * 
	 */
	private void deleteMovieConfirm() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(mMovie.getString(Movie.TITLE)).setMessage(getText(R.string.delete_confirm)).setCancelable(
				false).setPositiveButton(getText(android.R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				deleteMovie();
				dialog.dismiss();
			}
		}).setNegativeButton(getText(android.R.string.no), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * 
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
	 * 
	 */
	@SuppressWarnings("unchecked")
	private void reload() {

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		if (currentTask != null) {
			currentTask.cancel(true);
		}

		currentTask = new GetMovieListTask();
		currentTask.execute(params);

	}
}
