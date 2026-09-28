package net.reichholf.dreamdroid.room

import net.reichholf.dreamdroid.enigma.Service

/**
 * Room tab-strip and roster reads for the service pickers, which move to
 * `ServiceRepository` next (hilt-migration PR 9b). Delete this with them.
 */
object UserBouquetCache {
    const val KIND_TV: String = "TV"
    const val KIND_RADIO: String = "RADIO"

    suspend fun loadTabStripServices(dao: RosterDao, profileId: Int, kind: String): List<Service> =
        dao.getTabStrip(profileId, kind).map { row ->
            Service(row.serviceRef, row.name)
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
