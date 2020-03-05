package net.reichholf.dreamdroid.fragment.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.reichholf.dreamdroid.R;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
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
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
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
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int length;
			while ((length = is.read(buffer)) != -1) {
				baos.write(buffer, 0, length);
			}
			changelog = baos.toString("UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		IOUtils.closeQuietly(is); // don't forget to close your streams
		Markwon.setMarkdown(getDialog().findViewById(android.R.id.message), changelog);
	}
}
