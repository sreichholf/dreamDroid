package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.helpers.enigma2.Service

/**
 * TV hub bouquet row. Description markers are section headers. Spacers stay on
 * the service-card path so issue #115 can hide them separately.
 */
enum class TvHubServiceRowKind {
    MARKER_HEADER,
    CHANNEL,
    SPACER
}

/** `1:64:` description rows only. `1:832:` spacers are markers but not headers. */
fun tvHubDrawsMarkerHeader(ref: String?): Boolean = Service.isMarker(ref) && !Service.isSpacer(ref)

fun tvHubServiceRowKind(ref: String?): TvHubServiceRowKind = when {
    tvHubDrawsMarkerHeader(ref) -> TvHubServiceRowKind.MARKER_HEADER
    Service.isSpacer(ref) -> TvHubServiceRowKind.SPACER
    else -> TvHubServiceRowKind.CHANNEL
}
