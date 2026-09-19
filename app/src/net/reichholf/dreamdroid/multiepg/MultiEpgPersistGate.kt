package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService

/**
 * Fail-closed Room persist for MultiEPG chunks. Empty [knownTabRefs] never
 * writes (Provider / All Services must not land in the shared cache).
 *
 * Phone fills [knownTabRefs] from the hub tab strip. TV fills it from
 * [net.reichholf.dreamdroid.room.UserBouquetCache.userBouquetTabs] of the
 * live TV bouquet list — the TV hub does not write the phone tab strip.
 */
class MultiEpgPersistGate(private val excludedTabRefs: Collection<String>) {
    @Volatile
    var knownTabRefs: Collection<String> = emptyList()

    fun persist(ref: String): Boolean = EnigmaService.isCacheableUserBouquetContainer(
        ref,
        ref,
        knownTabRefs,
        excludedTabRefs
    )
}
