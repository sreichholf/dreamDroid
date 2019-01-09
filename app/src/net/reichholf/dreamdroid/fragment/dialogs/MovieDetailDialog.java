package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

/**
 * Created by Stephan on 05.05.2015.
 */
public class MovieDetailDialog extends BottomSheetActionDialog {
	private static String KEY_MOVIE = "movie";

	public static MovieDetailDialog newInstance(ExtendedHashMap movie) {
		MovieDetailDialog fragment = new MovieDetailDialog();
		Bundle args = new Bundle();
		args.putSerializable(KEY_MOVIE, movie);
		fragment.setArguments(args);
		return fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.movie_epg_dialog, null);

		ExtendedHashMap movie = (ExtendedHashMap) getArguments().getSerializable(KEY_MOVIE);
		String title = movie.getString(Movie.KEY_TITLE, "");
		String servicename = movie.getString(Movie.KEY_SERVICE_NAME, "");
		String descShort = movie.getString(Movie.KEY_DESCRIPTION, "");
		String descEx = movie.getString(Movie.KEY_DESCRIPTION_EXTENDED, "");
		String length = movie.getString(Movie.KEY_LENGTH, "");

		Toolbar tb = view.findViewById(R.id.toolbar_epg_detail);
		tb.setTitle(title);

		TextView textServiceName = view.findViewById(R.id.service_name);
		if ("".equals(servicename))
			textServiceName.setVisibility(View.GONE);
		else
			textServiceName.setText(servicename);

		TextView textShort = view.findViewById(R.id.epg_short);
		if ("".equals(descShort))
			textShort.setVisibility(View.GONE);
		else
			textShort.setText(descShort);

		TextView textLength = view.findViewById(R.id.movie_length);
		if ("".equals(length))
			textLength.setVisibility(View.GONE);
		else
			textLength.setText(length);

		TextView textDescEx = view.findViewById(R.id.epg_description_extended);
		textDescEx.setText(descEx);

		return view;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
		bottomSheetDialog.setOnShowListener(dialog -> {
			BottomSheetDialog d = (BottomSheetDialog) dialog;
			FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
			BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
			if (bottomSheetBehavior != null) {
				bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
			}
		});
		return bottomSheetDialog;
	}
}
