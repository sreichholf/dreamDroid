package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * Bouquet channel list: `/web/getservices` order is the roster, and
 * `/web/epgnownext` only supplies now/next for a matching service reference.
 *
 * Markers, directories, and `4097:` IPTV/webradio rows stay, with no invented
 * events when EPG has none. The service name is the roster name. Rows that
 * exist only in EPG are omitted. The first EPG row for a reference wins.
 * Blank references are not matched to each other.
 */
fun mergeBouquetNowNext(
    roster: List<Service>,
    epgRows: List<ServiceNowNext>
): List<ServiceNowNext> {
    val epgByRef = HashMap<String, ServiceNowNext>(epgRows.size)
    for (row in epgRows) {
        val ref = row.serviceReference
        if (ref.isEmpty() || epgByRef.containsKey(ref)) {
            continue
        }
        epgByRef[ref] = row
    }
    return roster.map { service ->
        val matched = if (service.reference.isEmpty()) {
            null
        } else {
            epgByRef[service.reference]
        }
        ServiceNowNext(
            serviceReference = service.reference,
            serviceName = service.name,
            now = matched?.now,
            next = matched?.next
        )
    }
}

/**
 * Hub bouquet load for phone and TV.
 *
 * A failed `getservices` call stays a failure so callers keep today's error
 * or cache path instead of an empty success. When the roster succeeds, a
 * failed or empty `epgnownext` still returns those services with empty
 * now/next.
 */
suspend fun loadBouquetServiceNowNext(
    context: Context,
    params: List<NameValuePair>
): EpgNowNextLoadResult {
    val roster = loadServiceList(context, serviceListParams(params))
    if (!roster.success) {
        return EpgNowNextLoadResult(false, emptyList(), roster.errorText)
    }
    val epg = loadEpgNowNext(context, params)
    val epgRows = if (epg.success) epg.rows else emptyList()
    return EpgNowNextLoadResult(
        true,
        mergeBouquetNowNext(roster.services, epgRows),
        null
    )
}

/**
 * `/web/getservices` reads `sRef`. A missing `sRef` is the TV bouquet index,
 * not the bouquet the user opened, so Favourites never shows its channels.
 *
 * Hub callers pass `bRef` for a `1:7:` bouquet because `/web/epgnownext` wants
 * that name, and `sRef` when opening a directory. The service list always
 * uses `sRef` with the same reference. EPG keeps [epgParams].
 */
internal fun serviceListParams(epgParams: List<NameValuePair>): List<NameValuePair> {
    val sRef = epgParams.firstOrNull { it.key() == "sRef" }?.value().orEmpty()
    val bRef = epgParams.firstOrNull { it.key() == "bRef" }?.value().orEmpty()
    val ref = sRef.ifEmpty { bRef }
    if (ref.isEmpty()) {
        return epgParams
    }
    return listOf(NameValuePair("sRef", ref))
}
