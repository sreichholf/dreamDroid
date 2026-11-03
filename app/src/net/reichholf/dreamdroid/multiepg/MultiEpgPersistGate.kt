package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService

/**
 * Fail-closed Room persist for MultiEPG chunks. Without known tabs nothing is written
 * (Provider / All Services must not land in the shared cache).
 *
 * The known tabs belong to one profile: phone fills them from that profile's hub tab strip,
 * TV from [net.reichholf.dreamdroid.data.ServiceRepository.userBouquetTabs] of its TV
 * bouquet list. A load bound to any other profile persists nothing.
 */
class MultiEpgPersistGate(private val excludedTabRefs: Collection<String>) {
    private class KnownTabs(val profileId: Int, val refs: Collection<String>)

    @Volatile
    private var known: KnownTabs? = null

    /** [refs] are the tabs of [profileId]; null [profileId] forgets every tab. */
    fun knowTabs(profileId: Int?, refs: Collection<String>) {
        known = profileId?.let { KnownTabs(it, refs) }
    }

    fun forget() {
        known = null
    }

    /**
     * Whether [ref] of [profileId] may be written. [tabRootRef] is the hub tab the container
     * was opened under, which may differ from [ref] for a nested folder. Without one, [ref] is
     * only cacheable when it is itself a known tab; an unknown nested folder fails closed.
     */
    fun persist(profileId: Int, ref: String, tabRootRef: String? = null): Boolean {
        val tabs = known?.takeIf { it.profileId == profileId }?.refs ?: return false
        val root = tabRootRef?.takeIf { it.isNotEmpty() && tabs.contains(it) }
            ?: ref.takeIf { tabs.contains(it) }
            ?: return false
        return EnigmaService.isCacheableUserBouquetContainer(
            ref,
            root,
            tabs,
            excludedTabRefs
        )
    }
}
