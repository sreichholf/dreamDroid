package net.reichholf.dreamdroid.video

import java.util.Locale
import java.util.MissingResourceException

/**
 * An audio track as the engine reports it. [language] is the ISO 639-1 or 639-2 code from the
 * stream, when the engine has one.
 */
data class AudioTrackOption(val id: Int, val name: String, val language: String? = null)

/**
 * Returns the first track matching a language query, or null to keep the player's default.
 *
 * The query is a 2- or 3-letter language code (`de`, `deu`, `ger`) or a language name in English
 * or in [deviceLocale] (`German`, `Deutsch`). Tracks are matched by their language code first,
 * then by their name.
 */
fun preferredAudioTrackId(
    query: String,
    tracks: List<AudioTrackOption>,
    deviceLocale: Locale = Locale.getDefault()
): Int? {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val queryCode = LanguageCodes.iso3(trimmed) ?: LanguageCodes.forName(trimmed, deviceLocale)
    if (queryCode != null) {
        tracks.firstOrNull { LanguageCodes.iso3(it.language) == queryCode }?.let { return it.id }
    }
    val names = queryCode?.let { LanguageCodes.displayNames(it, deviceLocale) }.orEmpty()
    return tracks.firstOrNull { track ->
        nameMatches(track.name, trimmed) || names.any { nameMatches(track.name, it) }
    }?.id
}

private fun nameMatches(name: String, query: String): Boolean {
    val lowerName = name.lowercase(Locale.ROOT)
    val lowerQuery = query.lowercase(Locale.ROOT)
    return if (lowerQuery.length <= 3) {
        lowerName.split(Regex("[^\\p{L}0-9]+")).any { it.startsWith(lowerQuery) }
    } else {
        lowerName.contains(lowerQuery)
    }
}

private object LanguageCodes {
    /** ISO 639-2/B codes that `Locale` does not know, mapped to their 639-2/T code. */
    private val bibliographic = mapOf(
        "alb" to "sqi",
        "arm" to "hye",
        "baq" to "eus",
        "bur" to "mya",
        "chi" to "zho",
        "cze" to "ces",
        "dut" to "nld",
        "fre" to "fra",
        "geo" to "kat",
        "ger" to "deu",
        "gre" to "ell",
        "ice" to "isl",
        "mac" to "mkd",
        "mao" to "mri",
        "may" to "msa",
        "per" to "fas",
        "rum" to "ron",
        "slo" to "slk",
        "tib" to "bod",
        "wel" to "cym"
    )

    private val twoLetterByIso3: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { code -> iso3Of(code)?.let { it to code } }.toMap()
    }

    /** The ISO 639-2/T code for a 2- or 3-letter code (`de-DE` counts as `de`), else null. */
    fun iso3(code: String?): String? {
        val language = code?.trim()?.substringBefore('-')?.substringBefore('_')
            ?.lowercase(Locale.ROOT)
            ?: return null
        if (language.length !in 2..3 || !language.all { it in 'a'..'z' }) {
            return null
        }
        return bibliographic[language] ?: iso3Of(language)
    }

    /** The ISO 639-2/T code of the language named [name] in [deviceLocale] or English. */
    fun forName(name: String, deviceLocale: Locale): String? = twoLetterByIso3.keys.firstOrNull {
        displayNames(it, deviceLocale).any { display -> display.equals(name, ignoreCase = true) }
    }

    /** The name of [iso3] in [deviceLocale] and in English. */
    fun displayNames(iso3: String, deviceLocale: Locale): List<String> {
        val locale = Locale.forLanguageTag(twoLetterByIso3[iso3] ?: iso3)
        return listOf(
            locale.getDisplayLanguage(deviceLocale),
            locale.getDisplayLanguage(Locale.ENGLISH)
        )
            .filter { it.isNotBlank() && !it.equals(iso3, ignoreCase = true) }
            .distinct()
    }

    private fun iso3Of(code: String): String? = try {
        Locale.forLanguageTag(code).isO3Language.takeIf { it.isNotEmpty() }
    } catch (_: MissingResourceException) {
        null
    }
}
