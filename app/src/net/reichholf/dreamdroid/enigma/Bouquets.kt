package net.reichholf.dreamdroid.enigma

/**
 * TV + Radio bouquet roots from fav lists (Phase 2.2i).
 * Public ArrayLists for Java callers that mutate/addAll.
 */
class Bouquets {
    @JvmField
    val tv: ArrayList<Service> = ArrayList()

    @JvmField
    val radio: ArrayList<Service> = ArrayList()
}
