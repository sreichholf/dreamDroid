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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.compose.ui.platform.ComposeView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseFragment;
import net.reichholf.dreamdroid.helpers.backup.BackupData;
import net.reichholf.dreamdroid.helpers.backup.BackupService;
import net.reichholf.dreamdroid.ui.backup.BackupProfileToggle;
import net.reichholf.dreamdroid.ui.backup.BackupScreenKt;
import net.reichholf.dreamdroid.ui.backup.BackupUiState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by GAigner on 01/09/18.
 * Compose Material 3 backup UI; export/import still use {@link BackupService}.
 */
public class BackupFragment extends BaseFragment {

	private static final String TAG = BackupFragment.class.getSimpleName();

	private BackupService mBackupService;
	private BackupData mBackupData;
	private BackupUiState mUiState;

	private ActivityResultLauncher<Intent> mPickImportFile = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(), result -> onImportFilePicked(result)
	);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mHasFabMain = false;
		super.onCreate(savedInstanceState);
		initTitles(getString(R.string.backup));
	}

	private void loadBackupData() {
		mBackupData = mBackupService.getBackupData();
	}

	private void refreshProfileToggles() {
		mUiState.setProfilesFromBackup(
				mBackupData.getProfiles(),
				DreamDroid.getCurrentProfile().getId(),
				getString(R.string.backup_current_profile)
		);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mBackupService = new BackupService(requireContext());
		mUiState = new BackupUiState();
		loadBackupData();
		refreshProfileToggles();

		ComposeView composeView = new ComposeView(requireContext());
		composeView.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
		));
		BackupScreenKt.bindBackupScreen(
				composeView,
				mUiState,
				() -> {
					doImport();
					return kotlin.Unit.INSTANCE;
				},
				() -> {
					doExport();
					loadBackupData();
					showToast(getString(R.string.backup_export_successful));
					return kotlin.Unit.INSTANCE;
				}
		);
		return composeView;
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
		if (!mUiState.getExportSettings()) {
			mBackupData.setSettings(null);
		}
		Set<Integer> excluded = new HashSet<>();
		for (BackupProfileToggle toggle : mUiState.getProfiles()) {
			if (!toggle.getChecked()) {
				excluded.add(toggle.getId());
			}
		}
		if (!excluded.isEmpty()) {
			mBackupData.getProfiles().removeIf(p -> excluded.contains(p.getId()));
		}
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
					refreshProfileToggles();
					showToast(getString(R.string.backup_import_successful));
				} catch (IOException e) {
					Log.e(TAG, "unable to readTextFromUri:" + uri, e);
					showToast(getString(R.string.backup_import_error));
				}
			}
		}
	}

	@NonNull
	private String readTextFromUri(Uri uri) throws IOException {
		InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		StringBuilder stringBuilder = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			stringBuilder.append(line);
		}
		return stringBuilder.toString();
	}

}
