package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.Movie;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetMovieListTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, String, Boolean> {
	@Nullable
	private ArrayList<Movie> mMovies;

	public GetMovieListTask(GetMovieListTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair> params) {
		mMovies = null;
		if (isCancelled()) {
			return false;
		}
		List<NameValuePair> requestParams = params != null ? params : new ArrayList<>();
		List<Movie> fetched = HttpFragmentHelper.fetchMovies(getHttpClient(), requestParams);
		if (getHttpClient().hasError()) {
			mMovies = new ArrayList<>();
			return false;
		}
		// Successful HTTP can still fail SAX; null means parse failure (not an empty movie list).
		if (fetched == null) {
			mMovies = new ArrayList<>();
			return false;
		}
		mMovies = new ArrayList<>(fetched);
		return true;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetMovieListTaskHandler handler = (GetMovieListTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result) && mMovies != null;
		List<Movie> movies = mMovies != null ? mMovies : Collections.emptyList();
		String errorText = null;
		if (!success) {
			if (getHttpClient().hasError()) {
				errorText = getErrorText();
			} else {
				errorText = handler.getString(net.reichholf.dreamdroid.R.string.error_parsing);
			}
		}
		handler.onMovieListReady(success, movies, errorText);
	}

	public interface GetMovieListTaskHandler extends AsyncHttpTaskBaseHandler {
		void onMovieListReady(boolean success, @NonNull List<Movie> movies, @Nullable String errorText);
	}
}
