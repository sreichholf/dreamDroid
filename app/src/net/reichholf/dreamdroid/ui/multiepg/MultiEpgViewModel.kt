package net.reichholf.dreamdroid.ui.multiepg

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.toEnigmaDisplayMessage
import net.reichholf.dreamdroid.multiepg.MultiEpgPersistGate
import net.reichholf.dreamdroid.multiepg.MultiEpgSession
import net.reichholf.dreamdroid.multiepg.MultiEpgSync
import net.reichholf.dreamdroid.multiepg.MultiEpgSyncHolder
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.TimerSnapshotStore
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

const val MULTI_EPG_VISIBLE_MINUTES_KEY: String = "multi_epg_visible_minutes"

/**
 * Owns the loaded [MultiEpgSession] and the saved visible-minute span.
 * Load work uses [viewModelScope], so leaving the destination does not cancel it.
 * This class is not a menu provider and does not hold a navigation handle.
 */
class MultiEpgViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val savedAccess = HandleMultiEpgVisibleMinutesAccess(savedStateHandle)

    var visibleMinutes by mutableIntStateOf(readMultiEpgVisibleMinutes(savedAccess))
        private set

    private val persistGate = MultiEpgPersistGate(
        UserBouquetCache.excludedHubTabRefs(getApplication())
    )

    val session: MultiEpgSession = createSession()

    private var appliedKey: Pair<Int, String>? = null
    private var inFlightKey: Pair<Int, String>? = null
    private var loadGeneration: Int = 0
    private var loadJob: Job? = null

    /**
     * Loads [bouquetRef] at [anchorSec] when [remountEpoch] or the bouquet changed.
     * A repeat call for the same pair leaves the in-flight or finished load alone.
     */
    fun ensureLoaded(remountEpoch: Int, bouquetRef: String, anchorSec: Long) {
        val key = remountEpoch to bouquetRef
        if (appliedKey == key || inFlightKey == key) {
            return
        }
        val generation = ++loadGeneration
        inFlightKey = key
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                fillKnownTabRefs()
                if (generation != loadGeneration) {
                    return@launch
                }
                appliedKey = key
                session.replaceAndLoad(bouquetRef, anchorSec)
            } finally {
                if (generation == loadGeneration) {
                    inFlightKey = null
                }
            }
        }
    }

    fun onVisibleMinutesChange(minutes: Int) {
        visibleMinutes = minutes
        writeMultiEpgVisibleMinutes(savedAccess, minutes)
    }

    override fun onCleared() {
        loadJob?.cancel()
        session.cancel()
        super.onCleared()
    }

    private suspend fun fillKnownTabRefs() {
        val profileId = DreamDroid.getCurrentProfile().id ?: return
        persistGate.knownTabRefs = AppDatabase.roster(getApplication()).getTabStripRefs(profileId)
    }

    private fun createSession(): MultiEpgSession {
        val app = getApplication<Application>()
        return MultiEpgSession(
            sync = MultiEpgSyncHolder.shared(app),
            scope = viewModelScope,
            profileId = { DreamDroid.getCurrentProfile().id ?: -1 },
            noBouquetMessage = app.getString(R.string.multiepg_sync_test_no_bouquet),
            fetchTimers = MultiEpgSync.httpFetchTimers(),
            loadBouquetServices = MultiEpgSync.httpFetchBouquet(),
            formatError = { error -> error.toEnigmaDisplayMessage(app) },
            persistBouquet = persistGate::persist,
            shouldSkipReceiverHttp = { hasCache ->
                SessionConnectionHolder.shared.status.value.shouldSkipReceiverHttp(hasCache)
            },
            isSessionOffline = {
                SessionConnectionHolder.shared.status.value.session ==
                    ConnectionStatus.Session.Offline
            },
            loadCachedRoster = { profileId, ref ->
                UserBouquetCache.loadRosterServices(
                    AppDatabase.roster(app),
                    profileId,
                    ref
                )
            },
            loadCachedTimers = { profileId ->
                TimerSnapshotStore.load(AppDatabase.timer(app), profileId)
            }
        )
    }
}

interface MultiEpgVisibleMinutesAccess {
    fun getVisibleMinutes(): Int?

    fun setVisibleMinutes(minutes: Int)
}

class MapMultiEpgVisibleMinutesAccess(
    private val values: MutableMap<String, Any> = mutableMapOf()
) : MultiEpgVisibleMinutesAccess {
    override fun getVisibleMinutes(): Int? = values[MULTI_EPG_VISIBLE_MINUTES_KEY] as? Int

    override fun setVisibleMinutes(minutes: Int) {
        values[MULTI_EPG_VISIBLE_MINUTES_KEY] = minutes
    }
}

private class HandleMultiEpgVisibleMinutesAccess(private val handle: SavedStateHandle) :
    MultiEpgVisibleMinutesAccess {
    override fun getVisibleMinutes(): Int? = handle.get<Int>(MULTI_EPG_VISIBLE_MINUTES_KEY)

    override fun setVisibleMinutes(minutes: Int) {
        handle[MULTI_EPG_VISIBLE_MINUTES_KEY] = minutes
    }
}

/** Absent key reads as [MULTI_EPG_VISIBLE_MINUTES] and is not written back. */
fun readMultiEpgVisibleMinutes(access: MultiEpgVisibleMinutesAccess): Int =
    access.getVisibleMinutes() ?: MULTI_EPG_VISIBLE_MINUTES

fun writeMultiEpgVisibleMinutes(access: MultiEpgVisibleMinutesAccess, minutes: Int) {
    access.setVisibleMinutes(minutes)
}
