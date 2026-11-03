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

    /**
     * Whether [ref] may be written. [tabRootRef] is the hub tab the container was opened
     * under, which may differ from [ref] for a nested folder. Without one, [ref] is only
     * cacheable when it is itself a known tab; an unknown nested folder fails closed.
     */
    fun persist(ref: String, tabRootRef: String? = null): Boolean {
        val known = knownTabRefs
        val root = tabRootRef?.takeIf { it.isNotEmpty() && known.contains(it) }
            ?: ref.takeIf { known.contains(it) }
            ?: return false
        return EnigmaService.isCacheableUserBouquetContainer(
            ref,
            root,
            known,
            excludedTabRefs
        )
    }
}
