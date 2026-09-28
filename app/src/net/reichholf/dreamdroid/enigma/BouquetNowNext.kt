package net.reichholf.dreamdroid.enigma

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
