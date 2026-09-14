package net.reichholf.dreamdroid.multiepg

/**
 * MultiEPG bouquet identity comes from NavHost leaf extras, not from saved
 * composition state. Drawer `restoreState` and remount must not keep a previous
 * bouquet or clock.
 */
object MultiEpgRestore {
    fun bouquetRef(argsRef: String?): String = argsRef.orEmpty()

    fun bouquetName(argsName: String?): String = argsName.orEmpty()

    /**
     * Drop the saved painted clock when the drawer remounts this leaf or the
     * bouquet extras change.
     */
    fun resetClock(
        remountEpoch: Int,
        bouquetRef: String,
        savedEpoch: Int,
        savedRef: String
    ): Boolean = remountEpoch != savedEpoch || bouquetRef != savedRef
}
