package net.reichholf.dreamdroid.ui.epg

/**
 * Bouquet EPG query time. A picked clock is kept as-is; recomposition must not
 * advance it to wall-clock "now".
 */
object EpgBouquetClock {
    fun keepPicked(pickedSec: Int, nowSec: Int): Int {
        return pickedSec
    }
}
