package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService

/** What a row of a bouquet list is, read from its reference. */
enum class BouquetEntryKind {
    /** A user bouquet in the TV or radio bouquet index. */
    Bouquet,

    /** A folder below the index: a satellite, a provider, or a nested bouquet. */
    Directory,

    /** A marker or a `1:832:` spacer. */
    Marker,

    /** A `1:134:` alternatives group. */
    Alternative,

    /** A reference with a path or URL, or a non-DVB service type. */
    Stream,

    Service
}

data class BouquetEntry(val reference: String, val name: String, val kind: BouquetEntryKind)

/**
 * The kind of [ref]. [atRoot] is true for the rows of a bouquet index, where `1:7:` is a
 * bouquet; elsewhere (satellite lists) `1:7:` is a folder. The plugin's `e2serviceisstream`
 * is 1 for any reference with a path, so it cannot tell streams apart.
 */
fun bouquetEntryKind(ref: String, atRoot: Boolean): BouquetEntryKind = when {
    atRoot && EnigmaService.isBouquet(ref) -> BouquetEntryKind.Bouquet
    EnigmaService.isMarker(ref) -> BouquetEntryKind.Marker
    ref.startsWith(ALTERNATIVE_PREFIX) -> BouquetEntryKind.Alternative
    EnigmaService.isDirectory(ref) -> BouquetEntryKind.Directory
    isStream(ref) -> BouquetEntryKind.Stream
    else -> BouquetEntryKind.Service
}

fun Service.toBouquetEntry(atRoot: Boolean): BouquetEntry =
    BouquetEntry(reference, name, bouquetEntryKind(reference, atRoot))

private fun isStream(ref: String): Boolean {
    val fields = ref.split(':')
    return fields[0] != DVB_SERVICE_TYPE || fields.getOrNull(PATH_FIELD).orEmpty().isNotEmpty()
}

private const val ALTERNATIVE_PREFIX = "1:134:"
private const val DVB_SERVICE_TYPE = "1"
private const val PATH_FIELD = 10
