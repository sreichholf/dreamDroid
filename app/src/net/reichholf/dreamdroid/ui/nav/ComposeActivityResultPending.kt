package net.reichholf.dreamdroid.ui.nav

data class PendingComposeActivityResult(val requestCode: Int, val resultCode: Int)

/**
 * A result that arrives while no compose destination is listening is held until
 * the next destination registers. Timer-edit save pops the hub first, then posts
 * the result; if that post runs before the hub listener is back, this holds it.
 */
fun holdComposeActivityResultIfDetached(
    listenerAttached: Boolean,
    incoming: PendingComposeActivityResult
): PendingComposeActivityResult? = if (listenerAttached) null else incoming

fun takePendingComposeActivityResult(
    listenerAttached: Boolean,
    pending: PendingComposeActivityResult?
): PendingComposeActivityResult? = if (listenerAttached) pending else null
