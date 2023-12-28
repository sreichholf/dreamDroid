package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseFragment;
import net.reichholf.dreamdroid.helpers.backup.BackupData;
import net.reichholf.dreamdroid.helpers.backup.BackupService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import java9.util.stream.StreamSupport;

/**
 * Created by GAigner on 01/09/18.
 */
public class BackupFragment extends BaseFragment {

	private static final String TAG = BackupFragment.class.getSimpleName();

	private BackupService mBackupService;
	private BackupData mBackupData;

	@NonNull
	private List<SwitchCompat> mProfileSwitches = new ArrayList<>();
	private SwitchCompat mSettingsSwitch;
	private View mBackupView;

	private ActivityResultLauncher<Intent> mPickImportFile = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(), result -> onImportFilePicked(result)
	);

	private void loadBackupData() {
		mBackupData = mBackupService.getBackupData();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mBackupService = new BackupService(getContext());
		mBackupView = inflater.inflate(R.layout.backup, null);
		mSettingsSwitch = mBackupView.findViewById(R.id.backup_export_settings);
		mBackupView.findViewById(R.id.buttonExport).setOnClickListener(view -> {
			doExport();
			loadBackupData();
			showToast(getString(R.string.backup_export_successful));
		});
		mBackupView.findViewById(R.id.buttonImport).setOnClickListener(view -> doImport());
		loadBackupData();
		refreshView();
		return mBackupView;
	}

	private void refreshView() {
		int currentProfileId = DreamDroid.getCurrentProfile().getId();
		LinearLayout checkboxLayout = mBackupView.findViewById(R.id.layout_backup_profile_dynamic);
		checkboxLayout.removeAllViews();
		mProfileSwitches.clear();
		for (Profile profile : mBackupData.getProfiles()) {
			int id = profile.getId();
			String text = String.format("%s (%s)", profile.getName(), profile.getHost());
			if (id == currentProfileId) {
				String currentText = getString(R.string.backup_current_profile);
				text += " (" + currentText + ")";
			}
			SwitchCompat sw = new SwitchCompat(getContext());
			sw.setText(text);
			sw.setId(id);
			sw.setChecked(true);
			checkboxLayout.addView(sw);
			mProfileSwitches.add(sw);
		}
	}

	private void doImport() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.setType("*/*");
		try {
			mPickImportFile.launch(intent);
		} catch (ActivityNotFoundException e) {
			showToast(e.getLocalizedMessage());
		}

	}

	private void doExport() {
		if (!mSettingsSwitch.isChecked()) {
			mBackupData.setSettings(null);
		}
		StreamSupport.stream(mProfileSwitches).filter(p -> !p.isChecked()).forEach(checkBox -> {
			mBackupData.getProfiles().remove(StreamSupport.stream(mBackupData.getProfiles()).filter(p -> p.getId() == checkBox.getId()).findFirst().get());
		});
		mBackupService.doExport(mBackupData);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	protected void onImportFilePicked(ActivityResult result) {
		if (result.getResultCode() == Activity.RESULT_OK) {
			Intent data = result.getData();
			if (data != null) {
				Uri uri = data.getData();
				try {
					mBackupService.doImport(readTextFromUri(uri));
					loadBackupData();
					refreshView();
					showToast(getString(R.string.backup_import_successful));
				} catch (IOException e) {
					Log.e(TAG, "unable to readTextFromUri:" + uri, e);
					showToast(getString(R.string.backup_import_error));
				}
			}
			return;
		}
	}

	@NonNull
	private String readTextFromUri(Uri uri) throws IOException {
		InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		StringBuilder stringBuilder = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			stringBuilder.append(line);
		}
		return stringBuilder.toString();
	}

}
