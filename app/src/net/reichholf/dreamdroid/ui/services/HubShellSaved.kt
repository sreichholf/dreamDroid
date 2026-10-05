package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.helpers.setOrRemove

object HubShellSavedKeys {
    const val MODE = "hub_mode"
    const val CURRENT_TV = "hub_current_tv"
    const val CURRENT_RADIO = "hub_current_radio"
    const val CURRENT_MOVIE = "hub_current_movie"
    const val SELECTED_ROW = "hub_selected_row"
    const val TIMER_REMOUNT_EPOCH = "hub_timer_remount_epoch"
    const val NOW_PLAYING_RELOAD_EPOCH = "hub_now_playing_reload_epoch"
}

data class HubShellSaved(
    val mode: String = HubModes.TV,
    val currentTv: String? = null,
    val currentRadio: String? = null,
    val currentMovie: String? = null,
    val selectedRow: Int = 0,
    val timerRemountEpoch: Int = 0,
    val nowPlayingReloadEpoch: Int = 0
)

/** Absent mode reads as TV. Absent row and epochs read as 0. Reading does not write. */
fun readHubShellSaved(handle: SavedStateHandle): HubShellSaved = HubShellSaved(
    mode = handle.get<String>(HubShellSavedKeys.MODE) ?: HubModes.TV,
    currentTv = handle.get<String>(HubShellSavedKeys.CURRENT_TV),
    currentRadio = handle.get<String>(HubShellSavedKeys.CURRENT_RADIO),
    currentMovie = handle.get<String>(HubShellSavedKeys.CURRENT_MOVIE),
    selectedRow = handle.get<Int>(HubShellSavedKeys.SELECTED_ROW) ?: 0,
    timerRemountEpoch = handle.get<Int>(HubShellSavedKeys.TIMER_REMOUNT_EPOCH) ?: 0,
    nowPlayingReloadEpoch = handle.get<Int>(HubShellSavedKeys.NOW_PLAYING_RELOAD_EPOCH) ?: 0
)

fun HubShellSaved.writeTo(handle: SavedStateHandle) {
    handle[HubShellSavedKeys.MODE] = mode
    handle.setOrRemove(HubShellSavedKeys.CURRENT_TV, currentTv)
    handle.setOrRemove(HubShellSavedKeys.CURRENT_RADIO, currentRadio)
    handle.setOrRemove(HubShellSavedKeys.CURRENT_MOVIE, currentMovie)
    handle[HubShellSavedKeys.SELECTED_ROW] = selectedRow
    handle[HubShellSavedKeys.TIMER_REMOUNT_EPOCH] = timerRemountEpoch
    handle[HubShellSavedKeys.NOW_PLAYING_RELOAD_EPOCH] = nowPlayingReloadEpoch
}

object HubModes {
    const val TV = "TV"
    const val RADIO = "Radio"
    const val MOVIES = "Movies"
    const val TIMER = "Timer"
}

/**
 * A hub child reloads when its load key changes. The same key, including a return
 * to a tab that already loaded, keeps the list.
 */
fun shouldLoadHubPage(appliedKey: String?, nextKey: String): Boolean = appliedKey != nextKey

/**
 * Movie locations load again on each hub entry until the receiver answered once; Room's strip
 * or the `/hdd/movie` stand-in alone does not stop that. Not while a load runs.
 */
fun shouldLoadHubLocations(jobActive: Boolean, fromReceiver: Boolean): Boolean =
    !jobActive && !fromReceiver
