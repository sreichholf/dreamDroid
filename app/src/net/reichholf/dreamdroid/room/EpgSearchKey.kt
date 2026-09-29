package net.reichholf.dreamdroid.room

import java.text.Normalizer
import java.util.Locale

/**
 * [text] folded for offline EPG title search, so "ärger" finds "ÄRGER" and "strasse" finds
 * "Straße". SQLite's `LIKE` and `lower()` fold ASCII only, so the key is built here and
 * stored in [EpgEventEntity.titleKey]. Upper- then lower-casing is Java's closest match to
 * full Unicode case folding; NFKC first makes composed and decomposed umlauts equal.
 */
internal fun epgSearchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .uppercase(Locale.ROOT)
    .lowercase(Locale.ROOT)
