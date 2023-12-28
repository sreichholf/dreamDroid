package net.reichholf.dreamdroid.helpers.backup;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.room.AppDatabase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import java9.util.function.Consumer;
import java9.util.stream.StreamSupport;

/**
 * Created by GAigner on 01/09/18.
 */
public class BackupService {
    private static final String TAG = BackupService.class.getSimpleName();
    private final Context mContext;
    private final SharedPreferences mPreferences;
    @NonNull
	private final Profile.ProfileDao mProfiles;

    public BackupService(Context context) {
        mContext = context;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mProfiles = AppDatabase.profiles(context);
    }

    @NonNull
	public BackupData getBackupData() {
        BackupData export = new BackupData();
        StreamSupport.stream(mPreferences.getAll().entrySet()).forEach((Consumer<Map.Entry<String, ?>>) entry -> {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            String type = entry.getValue().getClass().getSimpleName();
            export.addGenericSetting(new GenericSetting(key, value, type));
        });
        StreamSupport.stream(mProfiles.getProfiles()).forEach(profile -> export.addProfile(profile));
        return export;
    }

    public void doExport(BackupData data) {
        Gson gson = new GsonBuilder().create();
        String jsonContent = gson.toJson(data);
        PrintWriter out = null;
        try {
            String filename = "dreamdroid_backup.json";
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json");
            contentValues.put(MediaStore.Files.FileColumns.DATE_ADDED, System.currentTimeMillis()/1000);
            contentValues.put(MediaStore.Files.FileColumns.DATE_MODIFIED, System.currentTimeMillis()/1000);
            contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, true);
            Uri fileUri = mContext.getContentResolver().insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), contentValues);

            OutputStream os = mContext.getContentResolver().openOutputStream(fileUri, "w");
            os.write(jsonContent.getBytes());
            os.close();

            contentValues.clear();
            contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0);
            mContext.getContentResolver().update(fileUri, contentValues, null, null);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Export unable to create export file to write the backup to.", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void doImport(String content) {
        Log.i(TAG, "Import content: " + content);
        Gson gson = new GsonBuilder().create();
        BackupData backupData = gson.fromJson(content, BackupData.class);

        List<Profile> profiles = backupData.getProfiles();
        for (Profile profile : profiles) {
            Profile existingProfile = getProfileFromDB(profile.getName());
            if (existingProfile != null) {
                mProfiles.deleteProfile(existingProfile);
            }
            profile.setId( mProfiles.addProfile(profile) );
        }
        List<GenericSetting> settings = backupData.getSettings();
        Map<String, Object> allPreferences = (Map<String, Object>) mPreferences.getAll();

        for (GenericSetting setting : settings) {
            allPreferences.put(setting.getKey(), setting.getValue());
        }
    }

    @Nullable
	private Profile getProfileFromDB(String profileName) {
        List<Profile> all = mProfiles.getProfiles();
        for (Profile profile : all) {
            if (profile.getName().equals(profileName)) {
                return profile;
            }
        }
        return null;
    }

}
