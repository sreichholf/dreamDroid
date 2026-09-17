package net.reichholf.dreamdroid.helpers.enigma2

object Service {
    const val KEY_NAME: String = "servicename"
    const val KEY_REFERENCE: String = "reference"
    const val ROSTER_KIND_CHANNEL: String = "CHANNEL"
    const val ROSTER_KIND_DIRECTORY: String = "DIRECTORY"
    const val ROSTER_KIND_MARKER: String = "MARKER"

    enum class FLAGS(private val flag: Int) {
        IS_DIRECTORY(1),
        IS_MARKER(64),
        IS_GROUP(128),
        IS_LIVE(256);

        fun value(): Int = flag
    }

    fun getFlags(ref: String?): Int {
        if (ref.isNullOrEmpty()) return 0
        val f = try {
            ref.split(":")[1]
        } catch (_: ArrayIndexOutOfBoundsException) {
            return 0
        }
        return f.toInt()
    }

    fun isDirectory(ref: String?): Boolean {
        if (ref.isNullOrEmpty()) return false
        if ((getFlags(ref) and FLAGS.IS_DIRECTORY.value()) == FLAGS.IS_DIRECTORY.value()) {
            return true
        }
        // Provider / satellite / bouquet path nodes sometimes omit the directory flag bit.
        return ref.contains("FROM PROVIDERS") ||
            ref.contains("FROM SATELLITES") ||
            ref.contains("FROM BOUQUET")
    }

    fun isBouquet(ref: String): Boolean = ref.startsWith("1:7:")

    fun isMarker(ref: String?): Boolean =
        (getFlags(ref) and FLAGS.IS_MARKER.value()) == FLAGS.IS_MARKER.value()

    /**
     * Cache guard for user-bouquet tab strips and opened nested folders.
     * [ref] is the container being written; [tabRootRef] is the hub tab
     * (`HubServiceListPage.rootRef`), not a drilled `currentRef`.
     *
     * Fail closed when the tab strip / last HTTP bouquet list is unknown
     * ([knownUserBouquetTabRefs] empty) so Provider / All Services cannot leak.
     * [isDirectory] is too coarse to be this guard.
     */
    fun isCacheableUserBouquetContainer(
        ref: String?,
        tabRootRef: String?,
        knownUserBouquetTabRefs: Collection<String>,
        excludedTabRefs: Collection<String> = emptySet()
    ): Boolean {
        if (ref.isNullOrEmpty() || tabRootRef.isNullOrEmpty()) {
            return false
        }
        if (knownUserBouquetTabRefs.isEmpty()) {
            return false
        }
        if (!knownUserBouquetTabRefs.contains(tabRootRef)) {
            return false
        }
        if (tabRootRef in excludedTabRefs || ref in excludedTabRefs) {
            return false
        }
        if (tabRootRef.contains("FROM PROVIDERS")) {
            return false
        }
        return true
    }

    /** Roster row kind for an opened container: channel, directory, or marker. */
    fun rosterRowKind(ref: String?): String = when {
        isMarker(ref) -> ROSTER_KIND_MARKER
        isDirectory(ref) -> ROSTER_KIND_DIRECTORY
        else -> ROSTER_KIND_CHANNEL
    }
}
