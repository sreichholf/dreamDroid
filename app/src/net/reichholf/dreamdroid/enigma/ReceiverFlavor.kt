package net.reichholf.dreamdroid.enigma

/**
 * The web interface a receiver runs, as [ReceiverDetector] tells it. A profile whose flavor is
 * unknown (null) gets the Dreambox client, which is what every profile got before detection.
 */
enum class ReceiverFlavor {
    /** The Dreambox WebInterface: `/web` XML. */
    DreamboxWebIf,

    /** OpenWebif, on OpenATV, OpenPLi, OpenViX and other images: `/api` JSON next to `/web`. */
    OpenWebif
}
