package net.reichholf.dreamdroid.data

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Bouquets
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.mergeBouquetNowNext
import net.reichholf.dreamdroid.enigma.valueOrThrow
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.overlayNowNext
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.BouquetTabEntity
import net.reichholf.dreamdroid.room.ServiceRosterEntity
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/** Outcome of a TV + radio bouquet list load. */
sealed interface BouquetListLoad {
    /** Bouquets to show. [cached] ones are the Room tab strips. */
    data class Loaded(val bouquets: Bouquets, val cached: Boolean) : BouquetListLoad

    /** The receiver failed and Room has no tab strip to show instead. */
    data class Failed(val error: EnigmaHttpError?) : BouquetListLoad
}

/** Outcome of a service list load. */
sealed interface ServiceListLoad {
    /** Services to show. [cached] ones are the Room roster of that container. */
    data class Services(val services: List<Service>, val cached: Boolean) : ServiceListLoad

    /** The receiver failed and Room has no roster to show instead. */
    data class Failed(val error: EnigmaHttpError?) : ServiceListLoad
}

/** One step of a service list load with now/next. */
sealed interface NowNextListLoad {
    /** Rows to show. [cached] ones are the Room roster with now/next from Room EPG. */
    data class Rows(val rows: List<ServiceNowNext>, val cached: Boolean) : NowNextListLoad

    /** The receiver failed and Room has no roster to show instead. */
    data class Failed(val error: EnigmaHttpError?) : NowNextListLoad
}

/**
 * Service lists and bouquet rosters of the active profile, and the owner of the use-driven
 * offline cache (docs/offline-and-errors.md). Opening a hub bouquet tab writes the TV/radio
 * tab strip; opening a user-bouquet tab or a folder under one writes its roster. Provider,
 * All Services, and the aggregate bouquet index never land in Room.
 */
@Singleton
class ServiceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository,
    private val database: AppDatabase,
    private val sessions: SessionConnectionHolder
) {
    private val tvRoots: List<String> by lazy {
        context.resources.getStringArray(R.array.servicerefstv).toList()
    }

    private val radioRoots: List<String> by lazy {
        context.resources.getStringArray(R.array.servicerefsradio).toList()
    }

    /**
     * The dedicated TV and radio roots: the aggregate bouquet index, Provider, and All
     * Services. They are never a user bouquet tab and never persist.
     */
    val excludedTabRefs: Set<String> by lazy { LinkedHashSet(tvRoots + radioRoots) }

    /** The user bouquet tabs among [bouquets]: no dedicated root and no provider path. */
    fun userBouquetTabs(bouquets: List<Service>): List<Service> =
        userBouquetTabs(bouquets, excludedTabRefs)

    /** The Room tab strip refs, TV and radio, the bouquets MultiEPG and the hub persist. */
    suspend fun hubTabStripRefs(): List<String> {
        val profileId = profiles.requireCurrent().id ?: return emptyList()
        return database.rosterDao().getTabStripRefs(profileId)
    }

    /** The Room TV and radio tab strips. Empty when neither was written. */
    suspend fun cachedBouquets(): Bouquets {
        val bouquets = Bouquets()
        val profileId = profiles.requireCurrent().id ?: return bouquets
        bouquets.tv.addAll(tabStrip(profileId, KIND_TV))
        bouquets.radio.addAll(tabStrip(profileId, KIND_RADIO))
        return bouquets
    }

    /**
     * The children of the TV and radio bouquet indexes. Each list the receiver answered
     * replaces its Room tab strip. A failed TV request fails the load even when radio would
     * answer. On failure the Room tab strips answer when there are any.
     */
    suspend fun bouquets(): BouquetListLoad {
        val client = clients.current()
        val profileId = profiles.requireCurrent().id
        val tv = client.getServices(listOf(NameValuePair("sRef", tvRoots[0])))
        val tvList = tv.value
        var error = tv.error
        var success = false
        val live = Bouquets()
        if (tvList != null) {
            live.tv.addAll(tvList)
            profileId?.let { replaceTabStrip(it, KIND_TV, tvList) }
            val radio = client.getServices(listOf(NameValuePair("sRef", radioRoots[0])))
            val radioList = radio.value
            if (radioList != null) {
                live.radio.addAll(radioList)
                profileId?.let { replaceTabStrip(it, KIND_RADIO, radioList) }
            }
            success = radioList != null || tvList.isNotEmpty()
            error = radio.error
        }
        if (success) {
            return BouquetListLoad.Loaded(live, cached = false)
        }
        val cached = cachedBouquets()
        if (cached.tv.isEmpty() && cached.radio.isEmpty()) {
            return BouquetListLoad.Failed(error)
        }
        return BouquetListLoad.Loaded(cached, cached = true)
    }

    /**
     * TV bouquets for the TV MultiEPG picker. Room's TV tab strip answers while the session
     * skips the receiver, and when the receiver fails. Does not write the tab strip.
     */
    suspend fun tvBouquets(): List<Service> {
        val cached = profiles.requireCurrent().id?.let { tabStrip(it, KIND_TV) }.orEmpty()
        if (sessions.status.value.shouldSkipReceiverHttp(cached.isNotEmpty())) {
            return cached
        }
        return fetchTvBouquets().value ?: cached
    }

    /** The TV hub's bouquets from the receiver. An answer replaces the Room TV tab strip. */
    suspend fun tvBouquetTabs(): EnigmaResponse<List<Service>> {
        val response = fetchTvBouquets()
        val bouquets = response.value
        val profileId = profiles.requireCurrent().id
        if (bouquets != null && profileId != null) {
            replaceTabStrip(profileId, KIND_TV, bouquets)
        }
        return response
    }

    /** The Room TV tab strip, which the TV hub paints while it skips the receiver. */
    suspend fun cachedTvBouquetTabs(): List<Service> =
        profiles.requireCurrent().id?.let { tabStrip(it, KIND_TV) }.orEmpty()

    /** Members of [bouquetRef] from `/web/getservices`, the MultiEPG rows. Failures throw. */
    suspend fun bouquetServices(bouquetRef: String): List<Service> =
        clients.current().getServices(listOf(NameValuePair("sRef", bouquetRef))).valueOrThrow()

    /** The roster of [bouquetRef] in Room, or null when it was never written. */
    suspend fun cachedBouquetServices(profileId: Int, bouquetRef: String): List<Service>? =
        roster(profileId, bouquetRef)?.map { Service(it.serviceRef, it.name) }

    /**
     * Members of [ref] from the receiver. When it fails, the Room roster of [ref] answers
     * if it was written. Does not write the roster.
     */
    suspend fun services(ref: String): ServiceListLoad {
        val response = clients.current().getServices(listOf(NameValuePair("sRef", ref)))
        val live = response.value
        if (live != null) {
            return ServiceListLoad.Services(live, cached = false)
        }
        val cached = profiles.requireCurrent().id?.let { cachedBouquetServices(it, ref) }
            ?: return ServiceListLoad.Failed(response.error)
        return ServiceListLoad.Services(cached, cached = true)
    }

    /**
     * The hub list of [ref], opened under the hub tab [tabRootRef]. Unless [forceRefresh],
     * paints the Room roster first and skips the receiver while the session is Offline and
     * Room had it. Then asks the receiver; its answer replaces the roster when [ref] is
     * cacheable. When the receiver fails, Room is the fallback (also on a forced refresh)
     * before the failure is reported.
     */
    fun nowNextList(
        ref: String,
        tabRootRef: String,
        forceRefresh: Boolean = false
    ): Flow<NowNextListLoad> = flow {
        val painted = if (forceRefresh) null else cachedNowNext(ref)
        if (painted != null) {
            emit(NowNextListLoad.Rows(painted, cached = true))
        }
        if (!forceRefresh && sessions.status.value.shouldSkipReceiverHttp(painted != null)) {
            return@flow
        }
        val response = receiverNowNext(ref)
        val live = response.value
        if (live != null) {
            persistRoster(ref, tabRootRef, live)
            emit(NowNextListLoad.Rows(live, cached = false))
            return@flow
        }
        val fallback = cachedNowNext(ref)
        if (fallback != null) {
            emit(NowNextListLoad.Rows(fallback, cached = true))
        } else {
            emit(NowNextListLoad.Failed(response.error))
        }
    }

    /**
     * `/web/getservices` order is the roster; `/web/epgnownext` only adds now/next to it
     * (see [mergeBouquetNowNext]). A failed roster fails; a failed now/next leaves the rows
     * without events. `epgnownext` takes a `1:7:` bouquet as `bRef`, anything else as `sRef`.
     */
    suspend fun receiverNowNext(ref: String): EnigmaResponse<List<ServiceNowNext>> {
        val client = clients.current()
        val roster = client.getServices(listOf(NameValuePair("sRef", ref)))
        val services = roster.value ?: return EnigmaResponse(null, roster.error)
        val param = if (EnigmaService.isBouquet(ref)) "bRef" else "sRef"
        val uri = if (DreamDroid.featureNowNext()) URIStore.EPG_NOWNEXT else URIStore.EPG_NOW
        val epg = client.getEpgNowNext(listOf(NameValuePair(param, ref)), uri).value.orEmpty()
        return EnigmaResponse(mergeBouquetNowNext(services, epg))
    }

    /**
     * The Room roster of [ref] with now/next from the Room EPG chunk at [nowSec], or null
     * when that roster was never written.
     */
    suspend fun cachedNowNext(
        ref: String,
        nowSec: Long = System.currentTimeMillis() / 1000L
    ): List<ServiceNowNext>? {
        val profileId = profiles.requireCurrent().id ?: return null
        val rows = roster(profileId, ref)?.map { ServiceNowNext(it.serviceRef, it.name) }
            ?: return null
        val chunk = MultiEpgWindows.chunkContaining(nowSec)
        val events = database.epgDao().eventsOverlapping(
            profileId,
            ref,
            chunk.startSec,
            chunk.endSec
        )
        return overlayNowNext(rows, events, nowSec)
    }

    /**
     * Whether [ref], opened under the hub tab [tabRootRef], is a cacheable container: the
     * tab is in the Room tab strip and is not a dedicated root. Fails closed.
     */
    suspend fun isCacheableContainer(ref: String, tabRootRef: String): Boolean =
        EnigmaService.isCacheableUserBouquetContainer(
            ref,
            tabRootRef,
            hubTabStripRefs(),
            excludedTabRefs
        )

    /**
     * Replaces the Room roster of [ref] with [rows] when [ref] is cacheable under
     * [tabRootRef]. Returns whether it wrote.
     */
    suspend fun persistRoster(
        ref: String,
        tabRootRef: String,
        rows: List<ServiceNowNext>
    ): Boolean {
        val profileId = profiles.requireCurrent().id ?: return false
        if (!isCacheableContainer(ref, tabRootRef)) {
            return false
        }
        database.rosterDao().replaceRoster(
            profileId,
            ref,
            rows.mapIndexed { index, row ->
                ServiceRosterEntity(
                    profileId = profileId,
                    containerRef = ref,
                    position = index,
                    serviceRef = row.serviceReference,
                    name = row.serviceName,
                    kind = EnigmaService.rosterRowKind(row.serviceReference)
                )
            }
        )
        return true
    }

    /**
     * Drops the active profile's use-driven cache, or every profile's with [allProfiles].
     * A session that was Offline on cached data goes back to unknown.
     */
    suspend fun clearUseDrivenCache(allProfiles: Boolean) {
        if (allProfiles) {
            database.rosterDao().deleteAll()
            database.epgDao().deleteAll()
            database.timerDao().deleteAll()
            database.movieDao().deleteAll()
        } else {
            profiles.current.value?.id?.let { deleteCacheOf(it) }
        }
        sessions.onUseDrivenCacheCleared()
    }

    /** Drops the use-driven cache of the deleted profile [profileId]. */
    suspend fun clearCacheOfDeletedProfile(profileId: Int) {
        deleteCacheOf(profileId)
    }

    private suspend fun deleteCacheOf(profileId: Int) {
        database.rosterDao().deleteAllForProfile(profileId)
        database.epgDao().deleteAllForProfile(profileId)
        database.timerDao().deleteAllForProfile(profileId)
        database.movieDao().deleteAllForProfile(profileId)
    }

    private suspend fun fetchTvBouquets(): EnigmaResponse<List<Service>> =
        clients.current().getServices(listOf(NameValuePair("bRef", BOUQUETS_TV)))

    private suspend fun tabStrip(profileId: Int, kind: String): List<Service> =
        database.rosterDao().getTabStrip(profileId, kind).map { Service(it.serviceRef, it.name) }

    private suspend fun replaceTabStrip(profileId: Int, kind: String, loaded: List<Service>) {
        database.rosterDao().replaceTabStrip(
            profileId,
            kind,
            userBouquetTabs(loaded).mapIndexed { index, service ->
                BouquetTabEntity(
                    profileId = profileId,
                    kind = kind,
                    position = index,
                    serviceRef = service.reference,
                    name = service.name
                )
            }
        )
    }

    /** Null when [containerRef] was never written; empty when it was written empty. */
    private suspend fun roster(profileId: Int, containerRef: String): List<ServiceRosterEntity>? {
        val dao = database.rosterDao()
        if (dao.rosterContainerCount(profileId, containerRef) == 0) {
            return null
        }
        return dao.getRoster(profileId, containerRef)
    }

    private companion object {
        const val KIND_TV = "TV"
        const val KIND_RADIO = "RADIO"

        /**
         * The TV hub's bouquet query (`bRef`), formerly on RootBrowseFragment. Its quotes
         * are escaped, unlike the `servicerefstv` index the phone hub asks with `sRef`.
         */
        const val BOUQUETS_TV =
            "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || " +
                "(type == 25) FROM BOUQUET \\\"bouquets.tv\\\" ORDER BY bouquet"
    }
}

/** The user bouquet tabs among [loaded]: not in [excludedTabRefs], no provider path. */
fun userBouquetTabs(loaded: List<Service>, excludedTabRefs: Collection<String>): List<Service> =
    loaded.filter { service ->
        val ref = service.reference
        ref.isNotEmpty() && ref !in excludedTabRefs && !ref.contains("FROM PROVIDERS")
    }

/**
 * Transitional lookup for callers that are not Hilt-injected yet: the phone hub and zap
 * ViewModels (PR 10), the TV hub browse load (PR 12), and the player (PR 13). All run
 * after `DreamDroid` was injected. Delete with the last caller (docs/hilt-migration.md).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceRepositoryEntryPoint {
    fun serviceRepository(): ServiceRepository
}

fun serviceRepository(context: Context): ServiceRepository = EntryPointAccessors
    .fromApplication(context.applicationContext, ServiceRepositoryEntryPoint::class.java)
    .serviceRepository()
