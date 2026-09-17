package net.reichholf.dreamdroid.room

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService

/**
 * Use-driven TV/Radio tab-strip and roster writes. Callers pass the last HTTP
 * bouquet children or the Room strip; Provider / All / aggregate index never
 * persist.
 */
object UserBouquetCache {
    const val KIND_TV: String = "TV"
    const val KIND_RADIO: String = "RADIO"

    fun excludedHubTabRefs(context: Context): Set<String> {
        val tv = context.resources.getStringArray(R.array.servicerefstv)
        val radio = context.resources.getStringArray(R.array.servicerefsradio)
        val out = LinkedHashSet<String>(tv.size + radio.size)
        out.addAll(tv)
        out.addAll(radio)
        return out
    }

    fun userBouquetTabs(loaded: List<Service>, excludedTabRefs: Collection<String>): List<Service> =
        loaded.filter { service ->
            val ref = service.reference
            ref.isNotEmpty() &&
                ref !in excludedTabRefs &&
                !ref.contains("FROM PROVIDERS")
        }

    suspend fun replaceTabStrip(
        dao: RosterDao,
        profileId: Int,
        kind: String,
        loaded: List<Service>,
        excludedTabRefs: Collection<String>
    ) {
        val tabs = userBouquetTabs(loaded, excludedTabRefs)
        dao.replaceTabStrip(
            profileId,
            kind,
            tabs.mapIndexed { index, service ->
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

    suspend fun persistRosterIfCacheable(
        dao: RosterDao,
        profileId: Int,
        ref: String,
        tabRootRef: String,
        rows: List<ServiceNowNext>,
        excludedTabRefs: Collection<String>
    ): Boolean {
        val known = dao.getTabStripRefs(profileId)
        if (!EnigmaService.isCacheableUserBouquetContainer(
                ref,
                tabRootRef,
                known,
                excludedTabRefs
            )
        ) {
            return false
        }
        dao.replaceRoster(
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

    suspend fun loadTabStripServices(dao: RosterDao, profileId: Int, kind: String): List<Service> =
        dao.getTabStrip(profileId, kind).map { row ->
            Service(row.serviceRef, row.name)
        }

    /**
     * Cached roster for [containerRef], or null if this container was never written.
     * Empty list means we did write and the list had no rows.
     */
    suspend fun loadRosterNowNext(
        dao: RosterDao,
        profileId: Int,
        containerRef: String
    ): List<ServiceNowNext>? {
        if (dao.rosterContainerCount(profileId, containerRef) == 0) {
            return null
        }
        return dao.getRoster(profileId, containerRef).map { row ->
            ServiceNowNext(
                serviceReference = row.serviceRef,
                serviceName = row.name
            )
        }
    }

    suspend fun loadRosterServices(
        dao: RosterDao,
        profileId: Int,
        containerRef: String
    ): List<Service>? {
        if (dao.rosterContainerCount(profileId, containerRef) == 0) {
            return null
        }
        return dao.getRoster(profileId, containerRef).map { row ->
            Service(row.serviceRef, row.name)
        }
    }
}
