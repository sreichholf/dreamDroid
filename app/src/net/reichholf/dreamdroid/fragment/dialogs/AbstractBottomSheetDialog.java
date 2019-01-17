package net.reichholf.dreamdroid.fragment.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Created by reichi on 22/08/16.
 */
public class AbstractBottomSheetDialog extends BottomSheetDialogFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onDestroyView() {
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
		super.onSaveInstanceState(outState);
	}

	protected void setTextOrHide(TextView textView, String text) {
		if (text.isEmpty())
			textView.setVisibility(View.GONE);
		else
			textView.setText(text);
	}
}
