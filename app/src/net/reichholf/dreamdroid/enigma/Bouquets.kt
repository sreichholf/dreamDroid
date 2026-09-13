package net.reichholf.dreamdroid.enigma

/**
 * TV + Radio bouquet roots from fav lists (Phase 2.2i).
 * Mutable ArrayLists so callers can addAll.
 */
class Bouquets {
    val tv: ArrayList<Service> = ArrayList()

    val radio: ArrayList<Service> = ArrayList()
}
