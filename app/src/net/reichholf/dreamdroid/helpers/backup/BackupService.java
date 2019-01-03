package net.reichholf.dreamdroid.helpers.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.Profile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static android.os.Environment.*;

/**
 * Created by GAigner on 01/09/18.
 */
public class BackupService {
    private static final String TAG = BackupService.class.getSimpleName();
    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final DatabaseHelper mDatabase;

    public BackupService(Context context) {
        mContext = context;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mDatabase = DatabaseHelper.getInstance(mContext);
    }

    public BackupData getBackupData() {
        BackupData export = new BackupData();
        mPreferences.getAll().entrySet().stream().forEach((Consumer<Map.Entry<String, ?>>) entry -> {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            String type = entry.getValue().getClass().getSimpleName();
            export.addGenericSetting(new GenericSetting(key, value, type));
        });
        mDatabase.getProfiles().stream().forEach(profile -> export.addProfile(profile));
        return export;
    }

    public void doExport(BackupData data) {
        Gson gson = new GsonBuilder().create();
        String jsonContent = gson.toJson(data);
        PrintWriter out = null;
        try {
            out = new PrintWriter(new File(getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS), "dreamdroid_backup.json"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Export content: " + jsonContent);
        out.println(jsonContent);
        out.flush();
        out.close();
    }

    public void doImport(String content) {
        Log.i(TAG, "Import content: " + content);
        Gson gson = new GsonBuilder().create();
        BackupData backupData = gson.fromJson(content, BackupData.class);

        List<Profile> profiles = backupData.getProfiles();
        for (Profile profile : profiles) {
            Profile existingProfile = getProfileFromDB(profile.getName());
            if (existingProfile != null) {
                mDatabase.deleteProfile(existingProfile);
            }
            mDatabase.addProfile(profile);
        }
        List<GenericSetting> settings = backupData.getSettings();
        Map<String, Object> allPreferences = (Map<String, Object>) mPreferences.getAll();

        for (GenericSetting setting : settings) {
            allPreferences.put(setting.getKey(), setting.getValue());
        }
    }

    private Profile getProfileFromDB(String profileName) {
        List<Profile> all = mDatabase.getProfiles();
        for (Profile profile : all) {
            if (profile.getName().equals(profileName)) {
                return profile;
            }
        }
        return null;
    }

}
