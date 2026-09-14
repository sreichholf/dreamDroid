package net.reichholf.dreamdroid.multiepg

/**
 * Painted MultiEPG "now" instant. [sec] is a pure read so tests can fake the
 * wall clock; the destination ticks about once a minute without reloading EPG.
 */
object MultiEpgNowClock {
    const val TICK_MS: Long = 60_000L

    fun sec(clockMs: () -> Long = System::currentTimeMillis): Long = clockMs() / 1000L
}
