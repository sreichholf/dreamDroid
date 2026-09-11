package net.reichholf.dreamdroid.helpers.backup

import android.net.Uri
import net.reichholf.dreamdroid.Profile

class BackupData {
    private var mSettings: MutableList<GenericSetting>? = ArrayList()
    private var mProfiles: MutableList<Profile> = ArrayList()
    private var mUri: Uri? = null

    fun getSettings(): MutableList<GenericSetting>? = mSettings

    fun addGenericSetting(genericSetting: GenericSetting) {
        if (mSettings == null) {
            mSettings = ArrayList()
        }
        mSettings!!.add(genericSetting)
    }

    fun setSettings(settings: MutableList<GenericSetting>?) {
        mSettings = settings
    }

    fun getProfiles(): MutableList<Profile> = mProfiles

    fun addProfile(profile: Profile) {
        mProfiles.add(profile)
    }

    fun setProfiles(profiles: MutableList<Profile>) {
        mProfiles = profiles
    }

    fun getUri(): Uri? = mUri

    fun setUri(uri: Uri?) {
        mUri = uri
    }
}
