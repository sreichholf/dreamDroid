package net.reichholf.dreamdroid.helpers.backup;

import android.net.Uri;

import net.reichholf.dreamdroid.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by GAigner on 01/09/18.
 */
public class BackupData {
    private List<GenericSetting> mSettings = new ArrayList<>();
    private List<Profile> mProfiles = new ArrayList<>();

    private Uri mUri;

    public List<GenericSetting> getSettings() {
        return mSettings;
    }

    public void addGenericSetting(GenericSetting genericSetting) {
        mSettings.add(genericSetting);
    }

    public void setSettings(List<GenericSetting> settings) {
        mSettings = settings;
    }

    public List<Profile> getProfiles() {
        return mProfiles;
    }

    public void addProfile(Profile profile) {
        mProfiles.add(profile);
    }

    public void setProfiles(List<Profile> profiles) {
        mProfiles = profiles;
    }

    public Uri getUri() {
        return mUri;
    }

    public void setUri(Uri uri) {
        mUri = uri;
    }

}
