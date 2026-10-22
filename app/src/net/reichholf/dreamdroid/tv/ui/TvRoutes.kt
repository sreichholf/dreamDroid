package net.reichholf.dreamdroid.tv.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stable TV NavHost ids. The NavHost navigates the [Serializable] types. */
object TvRoutes {
    const val HUB = "tv_hub"
    const val SETTINGS = "tv_settings"
    const val PROFILES = "tv_profiles"
    const val BACKUP = "tv_backup"
}

@Serializable
@SerialName(TvRoutes.HUB)
data object TvHub

@Serializable
@SerialName(TvRoutes.SETTINGS)
data object TvSettings

@Serializable
@SerialName(TvRoutes.PROFILES)
data object TvProfiles

@Serializable
@SerialName(TvRoutes.BACKUP)
data object TvBackup
