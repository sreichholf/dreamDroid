package net.reichholf.dreamdroid.helpers.backup;

import net.reichholf.dreamdroid.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by GAigner on 01/09/18.
 */
public class BackupData {
    private List<GenericSetting> settings = new ArrayList<>();
    private List<Profile> profiles = new ArrayList<>();

    public List<GenericSetting> getSettings() {
        return settings;
    }

    public void addGenericSetting(GenericSetting genericSetting) {
        this.settings.add(genericSetting);
    }

    public void setSettings(List<GenericSetting> settings) {
        this.settings = settings;
    }

    public List<Profile> getProfiles() {
        return profiles;
    }

    public void addProfile(Profile profile) {
        this.profiles.add(profile);
    }

    public void setProfiles(List<Profile> profiles) {
        this.profiles = profiles;
    }


}
