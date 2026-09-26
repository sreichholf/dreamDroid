package net.reichholf.dreamdroid.ui.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.Serializable
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.enigma.loadEpgNowNext
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.services.bouquetsAfterHttpOrCache
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Zap list, zap position, and now/next for the player overlay, with their loads.
 * Scoped to [net.reichholf.dreamdroid.activities.VideoActivity] so a recreate keeps
 * the loaded list. [VideoOverlayController] observes [session] and keeps libVLC and
 * the views.
 */
class VideoPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableSession = MutableStateFlow(VideoPlaybackSession())
    val session: StateFlow<VideoPlaybackSession> = mutableSession.asStateFlow()

    private val errorChannel = Channel<String>(Channel.BUFFERED)
    val errors: Flow<String> = errorChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var bouquetJob: Job? = null

    /** Returns whether the title or a ref changed. */
    fun applyExtras(
        title: String?,
        serviceRef: String?,
        bouquetRef: String?,
        info: Serializable?
    ): Boolean {
        val before = mutableSession.value
        val after = before.withExtras(title, serviceRef, bouquetRef, info)
        mutableSession.value = after
        if (after.movie != null) {
            bouquetJob?.cancel()
            bouquetJob = null
        } else {
            loadBouquetBar()
        }
        return before.title != after.title ||
            before.serviceRef != after.serviceRef ||
            before.bouquetRef != after.bouquetRef
    }

    fun zapTo(row: ServiceNowNext) {
        mutableSession.update { it.zappedTo(row) }
    }

    /** Moves the zap position one row; returns false when the list has no neighbour. */
    fun step(forward: Boolean): Boolean {
        val row = mutableSession.value.neighbour(forward) ?: return false
        zapTo(row)
        return true
    }

    /** Returns false when [ref] is empty or already the bouquet. */
    fun selectBouquet(ref: String): Boolean {
        if (ref.isEmpty() || ref == mutableSession.value.bouquetRef) {
            return false
        }
        mutableSession.update { it.copy(bouquetRef = ref) }
        return true
    }

    fun reload() {
        val bouquetRef = mutableSession.value.bouquetRef
        if (bouquetRef.isNullOrEmpty()) {
            return
        }
        val app = getApplication<Application>()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = loadEpgNowNext(app, arrayListOf(NameValuePair("bRef", bouquetRef)))
            if (!result.success) {
                errorChannel.trySend(result.errorText ?: app.getString(R.string.get_content_error))
                return@launch
            }
            mutableSession.update { it.withServices(result.rows) }
        }
    }

    fun cancelReload() {
        loadJob?.cancel()
        loadJob = null
    }

    /** Cache first, then the receiver. One load per session; a recording clears it. */
    private fun loadBouquetBar() {
        if (bouquetJob != null) {
            return
        }
        bouquetJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val profileId = ProfileRepository.get().requireCurrent().id
            val dao = if (profileId != null) AppDatabase.roster(ctx) else null
            val excluded = UserBouquetCache.excludedHubTabRefs(ctx)
            val cachedTv = if (dao != null && profileId != null) {
                UserBouquetCache.loadTabStripServices(dao, profileId, UserBouquetCache.KIND_TV)
            } else {
                emptyList()
            }
            val cachedRadio = if (dao != null && profileId != null) {
                UserBouquetCache.loadTabStripServices(
                    dao,
                    profileId,
                    UserBouquetCache.KIND_RADIO
                )
            } else {
                emptyList()
            }
            val hasStrip = cachedTv.isNotEmpty() || cachedRadio.isNotEmpty()
            if (hasStrip) {
                publishBouquets(overlayBouquets(cachedTv, cachedRadio, excluded))
            }
            val status = SessionConnectionHolder.shared.status.value
            if (status.shouldSkipReceiverHttp(hasStrip)) {
                return@launch
            }
            val result = loadBouquetList(ctx)
            if (dao != null && profileId != null) {
                if (result.tvLoaded) {
                    UserBouquetCache.replaceTabStrip(
                        dao,
                        profileId,
                        UserBouquetCache.KIND_TV,
                        result.bouquets.tv,
                        excluded
                    )
                }
                if (result.radioLoaded) {
                    UserBouquetCache.replaceTabStrip(
                        dao,
                        profileId,
                        UserBouquetCache.KIND_RADIO,
                        result.bouquets.radio,
                        excluded
                    )
                }
            }
            val painted = bouquetsAfterHttpOrCache(
                result.success,
                result.bouquets,
                cachedTv,
                cachedRadio
            ).first
            publishBouquets(overlayBouquets(painted.tv, painted.radio, excluded))
        }
    }

    private fun publishBouquets(items: List<Service>) {
        mutableSession.update { session ->
            if (session.movie != null) session else session.copy(bouquets = items)
        }
    }
}
