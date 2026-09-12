package net.reichholf.dreamdroid.ui.services

import net.reichholf.dreamdroid.enigma.Service

/**
 * Resolves which bouquet tab to show on hub cold start / after bouquet roots load.
 * Prefers [currentRef] (session), then [defaultRef] (profile default bouquet),
 * otherwise row 0 — Favourites when the receiver returned bouquets.
 */
internal fun resolveBouquetSelection(
	items: List<Service>,
	currentRef: String?,
	defaultRef: String?,
): Pair<Int, String?> {
	if (items.isEmpty()) {
		return 0 to (currentRef ?: defaultRef)
	}
	val preferred = currentRef?.takeIf { it.isNotEmpty() } ?: defaultRef?.takeIf { it.isNotEmpty() }
	val idx = if (preferred.isNullOrEmpty()) {
		0
	} else {
		val found = items.indexOfFirst { it.reference == preferred }
		if (found >= 0) found else 0
	}
	return idx to items[idx].reference
}

internal fun buildDedicatedBouquets(
	loaded: List<Service>,
	labels: Array<String>,
	refs: Array<String>,
): List<Service> {
	val items = ArrayList<Service>()
	var start = 0
	if (loaded.isNotEmpty()) {
		start = 1
		items.addAll(loaded)
	}
	for (i in start until labels.size) {
		items.add(Service(refs[i], labels[i]))
	}
	return items
}
