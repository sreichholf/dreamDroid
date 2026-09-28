package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService

/**
 * Fail-closed Room persist for MultiEPG chunks. Empty [knownTabRefs] never
 * writes (Provider / All Services must not land in the shared cache).
 *
 * Phone fills [knownTabRefs] from the hub tab strip. TV fills it from
 * [net.reichholf.dreamdroid.data.ServiceRepository.userBouquetTabs] of the
 * TV bouquet list.
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
