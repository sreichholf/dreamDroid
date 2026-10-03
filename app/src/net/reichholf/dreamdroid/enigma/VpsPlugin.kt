package net.reichholf.dreamdroid.enigma

/**
 * The receiver's VPS plugin as the client of one operation sees it: [present] is the last
 * known answer ([ReceiverApi.plugins]). A client whose request finds the plugin gone calls
 * [onMissing] and sends that request without it.
 */
class VpsPlugin(val present: Boolean, val onMissing: () -> Unit = {}) {
    companion object {
        /** No VPS plugin known: timer requests go as they would without it. */
        val Absent = VpsPlugin(present = false)
    }
}
