package net.reichholf.dreamdroid.helpers.backup

import android.net.Uri
import com.google.gson.annotations.SerializedName
import net.reichholf.dreamdroid.Profile

class BackupData {
    @SerializedName("mSettings")
    var settings: MutableList<GenericSetting>? = ArrayList()

    @SerializedName("mProfiles")
    var profiles: MutableList<Profile> = ArrayList()

    @SerializedName("mUri")
    var uri: Uri? = null

    fun addGenericSetting(genericSetting: GenericSetting) {
        if (settings == null) {
            settings = ArrayList()
        }
        settings!!.add(genericSetting)
    }

    fun addProfile(profile: Profile) {
        profiles.add(profile)
    }
}
