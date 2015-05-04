package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;

/**
 * Created by Stephan on 05.05.2015.
 */
public class MovieDetailDialog extends AbstractDialog {
	private static String KEY_MOVIE = "movie";

	public static MovieDetailDialog newInstance(ExtendedHashMap movie){
		MovieDetailDialog fragment = new MovieDetailDialog();
		Bundle args = new Bundle();
		args.putParcelable(KEY_MOVIE, movie);
		fragment.setArguments(args);
		return fragment;
	}


	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ExtendedHashMap movie = getArguments().getParcelable(KEY_MOVIE);
		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
		Dialog dialog = builder
			.title(movie.getString(Movie.KEY_TITLE))
			.content(movie.getString(Movie.KEY_DESCRIPTION_EXTENDED))
			.positiveText(getString(R.string.ok))
			.build();

		return dialog;
	}
}
