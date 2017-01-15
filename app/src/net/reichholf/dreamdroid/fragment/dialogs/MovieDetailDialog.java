package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;

/**
 * Created by Stephan on 05.05.2015.
 */
public class MovieDetailDialog extends BottomSheetActionDialog {
	private static String KEY_MOVIE = "movie";

	public static MovieDetailDialog newInstance(ExtendedHashMap movie) {
		MovieDetailDialog fragment = new MovieDetailDialog();
		Bundle args = new Bundle();
		args.putParcelable(KEY_MOVIE, movie);
		fragment.setArguments(args);
		return fragment;
	}


	@Override
	public void setupDialog(Dialog dialog, int style) {
		super.setupDialog(dialog, style);
		View view = LayoutInflater.from(getContext()).inflate(R.layout.movie_epg_dialog, null);
		dialog.setContentView(view);

		ExtendedHashMap movie = getArguments().getParcelable(KEY_MOVIE);
		String title = movie.getString(Movie.KEY_TITLE, "");
		String servicename = movie.getString(Movie.KEY_SERVICE_NAME, "");
		String descShort = movie.getString(Movie.KEY_DESCRIPTION, "");
		String descEx = movie.getString(Movie.KEY_DESCRIPTION_EXTENDED, "");
		String length = movie.getString(Movie.KEY_LENGTH, "");

		Toolbar tb = (Toolbar) dialog.findViewById(R.id.toolbar_epg_detail);
		tb.setTitle(title);

		TextView textServiceName = (TextView) dialog.findViewById(R.id.service_name);
		if ("".equals(servicename))
			textServiceName.setVisibility(View.GONE);
		else
			textServiceName.setText(servicename);

		TextView textShort = (TextView) dialog.findViewById(R.id.epg_short);
		if ("".equals(descShort))
			textShort.setVisibility(View.GONE);
		else
			textShort.setText(descShort);

		TextView textLength = (TextView) dialog.findViewById(R.id.movie_length);
		if ("".equals(length))
			textLength.setVisibility(View.GONE);
		else
			textLength.setText(length);

		TextView textDescEx = (TextView) dialog.findViewById(R.id.epg_description_extended);
		textDescEx.setText(descEx);

		FrameLayout bottomSheet = (FrameLayout) dialog.findViewById(android.support.design.R.id.design_bottom_sheet);
		BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
		if (bottomSheetBehavior != null) {
			bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
		}
	}
}
