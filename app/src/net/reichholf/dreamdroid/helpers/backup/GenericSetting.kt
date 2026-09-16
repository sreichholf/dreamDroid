package net.reichholf.dreamdroid.helpers.backup

import com.google.gson.annotations.SerializedName

class GenericSetting(
    @SerializedName("mKey") val key: String,
    @SerializedName("mValue") val value: String,
    @SerializedName("mType") val type: String
)
