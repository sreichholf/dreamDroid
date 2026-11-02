package net.reichholf.dreamdroid.video

import java.util.Locale

/**
 * The track ids [Media3Engine] hands out: the group index and the index of the track inside
 * it, packed into one Int so a pick from the track dialog needs no parallel list.
 */
object Media3Tracks {
    /** No track of this type: the dialog's "off". */
    const val OFF: Int = -1

    fun id(groupIndex: Int, trackIndex: Int): Int = (groupIndex shl SHIFT) or trackIndex

    fun groupIndex(id: Int): Int = id shr SHIFT

    fun trackIndex(id: Int): Int = id and MASK

    /**
     * What the track dialog shows: the format's label, else its language, then the codec's
     * name from its sample MIME type — "Deutsch · AC3", or whichever of the two is known.
     */
    fun label(label: String?, language: String?, sampleMimeType: String?): String = listOfNotNull(
        label?.takeIf { it.isNotBlank() } ?: languageName(language),
        sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    private fun languageName(language: String?): String? = language?.takeIf { it.isNotBlank() }
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }

    private const val SHIFT = 16
    private const val MASK = 0xFFFF
}
