package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by Stephan on 05.05.2015.
 */
public class MovieDetailBottomSheet extends BottomSheetActionDialog {
    @BindView(R.id.toolbar_epg_detail)
    Toolbar mToolbar;

    @BindView(R.id.service)
    TextView mService;

    @BindView(R.id.length)
    TextView mLength;

    @BindView(R.id.filesize)
    TextView mFileSize;

    @BindView(R.id.description)
    TextView mDescription;

    @BindView(R.id.description_extended)
    TextView mDescriptionExtended;

    @BindView(R.id.tags)
    LinearLayout mTagsLayout;

    @BindView(R.id.date)
    TextView mDate;

    public static MovieDetailBottomSheet newInstance(Movie movie) {
        MovieDetailBottomSheet fragment = new MovieDetailBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(Movie.class.getSimpleName(), movie);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.cloneInContext(getActivity()).inflate(R.layout.movie_epg_dialog, null);
        ButterKnife.bind(this, view);
        Movie movie = (Movie) getArguments().getSerializable(Movie.class.getSimpleName());

        mToolbar.setTitle(movie.title());

        setTextOrHide(mService, movie.serviceName());
        setTextOrHide(mLength, movie.length());
        setTextOrHide(mFileSize, movie.fileSizeReadable());
        setTextOrHide(mDate, movie.timeReadable());
        setTextOrHide(mDescription, movie.description());
        setTextOrHide(mDescriptionExtended, movie.descriptionExtended());

        int _16dp = (int) view.getResources().getDimension(R.dimen.full_padding);
        int _8dp = (int) view.getResources().getDimension(R.dimen.half_padding);
        float corner = view.getResources().getDimension(R.dimen.genre_corner);

        mTagsLayout.removeAllViews();
        for (String tag : movie.tags()) {
            TextView tv = new TextView(view.getContext());
            tv.setText(tag);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(corner);
            shape.setColor(ContextCompat.getColor(view.getContext(), R.color.details_element_background_dark));
            tv.setPadding(_8dp, _8dp, _8dp, _8dp);
            tv.setBackground(shape);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            params.setMargins(0, 0, _16dp, 0);
            mTagsLayout.setLayoutParams(params);
            mTagsLayout.addView(tv);
        }
        if (movie.tags().isEmpty())
            mTagsLayout.setVisibility(View.GONE);
        else
            mTagsLayout.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(dialog -> {
            BottomSheetDialog d = (BottomSheetDialog) dialog;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            if (bottomSheetBehavior != null)
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        return bottomSheetDialog;
    }
}
