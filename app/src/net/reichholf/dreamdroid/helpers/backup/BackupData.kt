package net.reichholf.dreamdroid.helpers.backup

import android.net.Uri
import com.google.gson.annotations.SerializedName
import net.reichholf.dreamdroid.Profile

class BackupData {
    @SerializedName("mSettings")
    var settings: MutableList<GenericSetting>? = ArrayList()

    @SerializedName("mProfiles")
    var profiles: MutableList<Profile> = ArrayList()

    /**
     * Null means a legacy file: receiver passwords are included.
     * Export writes true when passwords are kept and false when they are cleared.
     */
    @SerializedName("passwordsIncluded")
    var passwordsIncluded: Boolean? = null

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
