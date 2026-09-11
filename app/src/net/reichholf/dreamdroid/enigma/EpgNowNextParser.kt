package net.reichholf.dreamdroid.enigma

/**
 * Parses `/web/epgnownext` XML into paired [ServiceNowNext] rows.
 *
 * Matches legacy EPG now/next list pairing:
 * consecutive `<e2event>` elements are positional now/next pairs. A trailing odd event becomes a
 * now-only row.
 *
 * Do **not** use [parse] for flat `/web/epgnow` responses — [EnigmaClient.getEpgNowNext] maps those
 * one event per row without pairing.
 */
object EpgNowNextParser {
    fun parse(xml: String): List<ServiceNowNext> {
        return pairEvents(EventParser.parse(xml))
    }

    fun pairEvents(events: List<Event>): List<ServiceNowNext> {
        if (events.isEmpty()) {
            return emptyList()
        }
        val rows = ArrayList<ServiceNowNext>((events.size + 1) / 2)
        var i = 0
        while (i < events.size) {
            val now = events[i]
            val next = if (i + 1 < events.size) events[i + 1] else null
            rows.add(
                ServiceNowNext(
                    serviceReference = now.serviceReference,
                    serviceName = now.serviceName,
                    now = now,
                    next = next,
                )
            )
            i += if (next != null) 2 else 1
        }
        return rows
    }
}
