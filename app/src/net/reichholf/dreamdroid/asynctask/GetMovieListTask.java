package net.reichholf.dreamdroid.asynctask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.enigma.Movie;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.util.ArrayList;
import java.util.List;

public class GetMovieListTask extends AsyncHttpTaskBase<ArrayList<NameValuePair>, String, Boolean> {
	private ArrayList<Movie> mMovies;

	public GetMovieListTask(GetMovieListTaskHandler taskHandler) {
		super(taskHandler);
	}

	@NonNull
	@Override
	protected Boolean doInBackground(ArrayList<NameValuePair> params) {
		mMovies = new ArrayList<>();
		if (isCancelled()) {
			return false;
		}
		List<NameValuePair> requestParams = params != null ? params : new ArrayList<>();
		mMovies.addAll(HttpFragmentHelper.fetchMovies(getHttpClient(), requestParams));
		return !getHttpClient().hasError();
	}

	@Override
	protected void onPostExecute(Boolean result) {
		GetMovieListTaskHandler handler = (GetMovieListTaskHandler) mTaskHandler.get();
		if (isInvalid(handler)) {
			return;
		}
		boolean success = Boolean.TRUE.equals(result);
		handler.onMovieListReady(success, mMovies, success ? null : getErrorText());
	}

	public interface GetMovieListTaskHandler extends AsyncHttpTaskBaseHandler {
		void onMovieListReady(boolean success, @NonNull List<Movie> movies, @Nullable String errorText);
	}
}
