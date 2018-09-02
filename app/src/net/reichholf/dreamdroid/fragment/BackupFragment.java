/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;

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

import static net.reichholf.dreamdroid.helpers.Statics.*;

/**
 * Created by GAigner on 01/09/18.
 */
public class BackupFragment extends BaseFragment {
	private static final String TAG = BackupFragment.class.getSimpleName();

	private LayoutInflater mInflater;
	private BackupService service;
	private BackupData data;

	private List<CheckBox> profilesCheckBox = new ArrayList<>();
	private CheckBox settingsCheckBox;
	private View backupView;

    @Override
	public void onCreate(Bundle savedInstanceState) {
		mHasFabMain = true;
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		initTitles(getString(R.string.backup));
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.backup, menu);
	}

	private void loadBackupData() {
        this.data = service.getBackupData();
    }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.mInflater = getLayoutInflater(savedInstanceState);
		this.service =  new BackupService(getContext());
		this.backupView = mInflater.inflate(R.layout.backup, null);
		settingsCheckBox = this.backupView.findViewById(R.id.backup_export_settings);
		loadBackupData();
        refreshView();
		return backupView;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case (ITEM_BACKUP_EXPORT):
				doExport();
                loadBackupData();
				break;
			case ITEM_BACKUP_IMPORT:
				doImport();
                loadBackupData();
                refreshView();
				break;
			default:
				return false;
		}
		return false;
	}

    private void refreshView() {
		int currentProfileId = DreamDroid.getCurrentProfile().getId();
		LinearLayout checkboxLayout = this.backupView.findViewById(R.id.layout_backup_profile_dynamic);
        checkboxLayout.removeAllViews();
		profilesCheckBox.clear();
        for (Profile profile : data.getProfiles()) {
        	int id = profile.getId();
        	String text = profile.getName();
        	if (id==currentProfileId) {
				String currentText = getResources().getString(R.string.backup_current_profile);
        		text += " (" + currentText + ")";
			}
            CheckBox ch = new CheckBox(getContext());
            ch.setText(text);
            ch.setId(id);
            ch.setChecked(true);
            checkboxLayout.addView(ch);
            profilesCheckBox.add(ch);
        }
    }

    private void doImport() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.setType("*/*");
		getActivity().startActivityForResult(intent, REQUEST_BACKUP_IMPORT);
	}

	private void doExport() {
		if (!settingsCheckBox.isChecked()) {
			data.setSettings(null);
		}
		profilesCheckBox.stream().filter(p -> !p.isChecked()).forEach(checkBox -> {
            data.getProfiles().remove(data.getProfiles().stream().filter(p -> p.getId()== checkBox.getId()).findFirst().get());
        });
		service.doExport(data);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (REQUEST_BACKUP_IMPORT == requestCode && resultCode == Activity.RESULT_OK) {
			if (data != null) {
				Uri uri = data.getData();
				try {
					service.doImport(readTextFromUri(uri));
				} catch (IOException e) {
					Log.e(TAG, "unable to readTextFromUri:" + uri, e);
				}
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

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
