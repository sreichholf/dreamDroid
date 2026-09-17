package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.room.RosterDao

/**
 * Use-driven `/web/epgmulti` into the shared Room chunks. Persist only when
 * [containerRef] is a cacheable user-bouquet tab or a nested folder under one.
 * Non-cacheable refs (Provider / All / aggregate index) return empty without
 * fetching so hub fill never `epgmulti`s those containers.
 */
object UserBouquetEpgFill {
    suspend fun ensureNowChunk(
        sync: MultiEpgSync,
        rosterDao: RosterDao,
        profileId: Int,
        containerRef: String,
        tabRootRef: String,
        excludedTabRefs: Collection<String>,
        unixSec: Long,
        forceRefresh: Boolean = false
    ): List<Event> {
        val known = rosterDao.getTabStripRefs(profileId)
        val persist = EnigmaService.isCacheableUserBouquetContainer(
            containerRef,
            tabRootRef,
            known,
            excludedTabRefs
        )
        if (!persist) {
            return emptyList()
        }
        return sync.ensureChunk(
            profileId = profileId,
            bouquetRef = containerRef,
            unixSec = unixSec,
            forceRefresh = forceRefresh,
            persist = true
        )
    }
}
