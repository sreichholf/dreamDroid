package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import net.reichholf.dreamdroid.R;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

import ru.noties.markwon.Markwon;

public class ChangelogDialog extends DialogFragment {
	public static ChangelogDialog newInstance() {
		
		Bundle args = new Bundle();
		
		ChangelogDialog fragment = new ChangelogDialog();
		fragment.setArguments(args);
		return fragment;
	}
	
	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
				.setTitle(R.string.changelog)
				.setMessage(R.string.loading)
				.setPositiveButton(R.string.close, (dialog, which) -> dismiss());
		return builder.create();
	}

	@Override
	public void onStart() {
		super.onStart();
		String changelog = "";
		InputStream is = getResources().openRawResource(R.raw.changelog);
		try {
			changelog = IOUtils.toString(is);
		} catch (IOException e) {
			e.printStackTrace();
		}
		IOUtils.closeQuietly(is); // don't forget to close your streams
		Markwon.setMarkdown(getDialog().findViewById(android.R.id.message), changelog);
	}
}
