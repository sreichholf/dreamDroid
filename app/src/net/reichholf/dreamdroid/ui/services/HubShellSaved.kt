package net.reichholf.dreamdroid.ui.services

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

interface HubShellSavedAccess {
    fun getMode(): String?
    fun setMode(mode: String)
    fun getCurrentTv(): String?
    fun setCurrentTv(currentTv: String?)
    fun getCurrentRadio(): String?
    fun setCurrentRadio(currentRadio: String?)
    fun getCurrentMovie(): String?
    fun setCurrentMovie(currentMovie: String?)
    fun getSelectedRow(): Int?
    fun setSelectedRow(selectedRow: Int)
    fun getTimerRemountEpoch(): Int?
    fun setTimerRemountEpoch(epoch: Int)
    fun getNowPlayingReloadEpoch(): Int?
    fun setNowPlayingReloadEpoch(epoch: Int)
}

class MapHubShellSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    HubShellSavedAccess {
    override fun getMode(): String? = values[HubShellSavedKeys.MODE] as? String

    override fun setMode(mode: String) {
        values[HubShellSavedKeys.MODE] = mode
    }

    override fun getCurrentTv(): String? = values[HubShellSavedKeys.CURRENT_TV] as? String

    override fun setCurrentTv(currentTv: String?) {
        putOrRemove(HubShellSavedKeys.CURRENT_TV, currentTv)
    }

    override fun getCurrentRadio(): String? = values[HubShellSavedKeys.CURRENT_RADIO] as? String

    override fun setCurrentRadio(currentRadio: String?) {
        putOrRemove(HubShellSavedKeys.CURRENT_RADIO, currentRadio)
    }

    override fun getCurrentMovie(): String? = values[HubShellSavedKeys.CURRENT_MOVIE] as? String

    override fun setCurrentMovie(currentMovie: String?) {
        putOrRemove(HubShellSavedKeys.CURRENT_MOVIE, currentMovie)
    }

    override fun getSelectedRow(): Int? = values[HubShellSavedKeys.SELECTED_ROW] as? Int

    override fun setSelectedRow(selectedRow: Int) {
        values[HubShellSavedKeys.SELECTED_ROW] = selectedRow
    }

    override fun getTimerRemountEpoch(): Int? =
        values[HubShellSavedKeys.TIMER_REMOUNT_EPOCH] as? Int

    override fun setTimerRemountEpoch(epoch: Int) {
        values[HubShellSavedKeys.TIMER_REMOUNT_EPOCH] = epoch
    }

    override fun getNowPlayingReloadEpoch(): Int? =
        values[HubShellSavedKeys.NOW_PLAYING_RELOAD_EPOCH] as? Int

    override fun setNowPlayingReloadEpoch(epoch: Int) {
        values[HubShellSavedKeys.NOW_PLAYING_RELOAD_EPOCH] = epoch
    }

    private fun putOrRemove(key: String, value: String?) {
        if (value == null) {
            values.remove(key)
        } else {
            values[key] = value
        }
    }
}

/** Absent mode reads as TV. Absent row and epochs read as 0. Reading does not write. */
fun readHubShellSaved(access: HubShellSavedAccess): HubShellSaved = HubShellSaved(
    mode = access.getMode() ?: HubModes.TV,
    currentTv = access.getCurrentTv(),
    currentRadio = access.getCurrentRadio(),
    currentMovie = access.getCurrentMovie(),
    selectedRow = access.getSelectedRow() ?: 0,
    timerRemountEpoch = access.getTimerRemountEpoch() ?: 0,
    nowPlayingReloadEpoch = access.getNowPlayingReloadEpoch() ?: 0
)

fun HubShellSaved.writeTo(access: HubShellSavedAccess) {
    access.setMode(mode)
    access.setCurrentTv(currentTv)
    access.setCurrentRadio(currentRadio)
    access.setCurrentMovie(currentMovie)
    access.setSelectedRow(selectedRow)
    access.setTimerRemountEpoch(timerRemountEpoch)
    access.setNowPlayingReloadEpoch(nowPlayingReloadEpoch)
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
 * Movie locations load once while a strip is on screen. An empty failed load
 * runs again when the hub destination re-enters.
 */
fun shouldRetryHubLocations(
    ready: Boolean,
    locations: List<String>,
    jobActive: Boolean,
    lastHttpSuccess: Boolean?
): Boolean {
    if (jobActive) {
        return false
    }
    if (!ready) {
        return true
    }
    if (locations.isNotEmpty()) {
        return false
    }
    return lastHttpSuccess != true
}
