package net.reichholf.dreamdroid.video

/**
 * When a live stream that failed or ended is opened again.
 *
 * The receiver drops a live stream when the tuner is taken or the network hiccups. A live stream
 * has no end, so its end is a dropout too. After each failure [onFailure] gives the wait before
 * the next attempt, with longer waits each time, until the attempts run out. Only a stream that
 * played again ([onPlayed]) starts the count over, so a stream that keeps failing to start
 * gives up instead of reconnecting forever.
 */
class LiveReconnect(private val delaysMs: List<Long> = DEFAULT_DELAYS_MS) {
    private var failures = 0

    /** The wait before the next attempt, or null to give up. */
    fun onFailure(): Long? = delaysMs.getOrNull(failures++)

    /** The stream showed frames again. */
    fun onPlayed() {
        failures = 0
    }

    companion object {
        val DEFAULT_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L)
    }
}
