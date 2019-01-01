package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;

public class IndeterminateProgress extends DialogFragment {
	public static String ARG_TITLE = "title";
	public static String ARG_CONTENT = "content";


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
		MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
		builder.progress(true, 0)
				.progressIndeterminateStyle(true)
				.title(getArguments().getInt(ARG_TITLE))
				.content(getArguments().getInt(ARG_CONTENT))
				.cancelable(false);
		return builder.build();
	}
}
