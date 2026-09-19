package net.reichholf.dreamdroid.ui.epg

/**
 * List EPG bouquet identity. Drawer extras seed a blank session (first open /
 * remount via rememberSaveable). A picker result is the active bouquet until
 * the next remount — never copy stale leaf extras over a different current ref.
 */
object EpgBouquetRestore {
    fun resolveRef(leafRef: String?, currentRef: String): String =
        currentRef.ifEmpty { leafRef.orEmpty() }

    fun resolveName(leafName: String?, currentName: String, currentRef: String): String =
        if (currentRef.isNotEmpty()) currentName else leafName.orEmpty()
}
