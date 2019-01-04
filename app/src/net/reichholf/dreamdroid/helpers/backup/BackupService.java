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

import java9.util.function.Consumer;
import java9.util.stream.StreamSupport;

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
        StreamSupport.stream(mPreferences.getAll().entrySet()).forEach((Consumer<Map.Entry<String, ?>>) entry -> {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            String type = entry.getValue().getClass().getSimpleName();
            export.addGenericSetting(new GenericSetting(key, value, type));
        });
        StreamSupport.stream(mDatabase.getProfiles()).forEach(profile -> export.addProfile(profile));
        return export;
    }

    public void doExport(BackupData data) {
        Gson gson = new GsonBuilder().create();
        String jsonContent = gson.toJson(data);
        PrintWriter out = null;
        try {
            File backupDirectory = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
            File backupFile = new File(backupDirectory, "dreamdroid_backup.json");
            out = new PrintWriter(backupFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Export unable to create export file to write the backup to.", e);
            return;
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
