package net.reichholf.dreamdroid.tv.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stable TV NavHost ids. The NavHost navigates the [Serializable] types. */
object TvRoutes {
    const val HUB = "tv_hub"
    const val MULTI_EPG = "tv_multi_epg"
    const val SETTINGS = "tv_settings"
    const val PROFILES = "tv_profiles"
}

@Serializable
@SerialName(TvRoutes.HUB)
data object TvHub

@Serializable
@SerialName(TvRoutes.MULTI_EPG)
data class TvMultiEpg(val bouquetRef: String = "", val bouquetName: String = "")

@Serializable
@SerialName(TvRoutes.SETTINGS)
data object TvSettings

@Serializable
@SerialName(TvRoutes.PROFILES)
data object TvProfiles
