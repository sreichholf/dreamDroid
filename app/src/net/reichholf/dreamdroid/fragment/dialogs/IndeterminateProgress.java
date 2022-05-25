package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class IndeterminateProgress extends DialogFragment {
	@NonNull
	public static String ARG_TITLE = "title";
	@NonNull
	public static String ARG_CONTENT = "content";


	@NonNull
	public static IndeterminateProgress newInstance(int titleId, int contentId) {

		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, titleId);
		args.putInt(ARG_CONTENT, contentId);
		IndeterminateProgress fragment = new IndeterminateProgress();
		fragment.setArguments(args);
		return fragment;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
		builder.setMessage(getArguments().getInt(ARG_CONTENT))
				.setTitle(getArguments().getInt(ARG_TITLE))
				.setCancelable(false);
		return builder.create();
	}
}
