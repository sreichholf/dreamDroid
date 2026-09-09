package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * Typed `/web/signal` frontend status. Raw strings keep the box formatting for
 * labels; [snrPercent] / [snrDb] are scrubbed for the gauge and acoustic feedback.
 */
data class Signal(
    val snrDbRaw: String = "",
    val snrRaw: String = "",
    val berRaw: String = "",
    val agcRaw: String = "",
) : Serializable {
    val snrPercent: Int
        get() {
            val scrubbed = snrRaw.replace("%", "").trim()
            return try {
                scrubbed.toInt()
            } catch (e: NumberFormatException) {
                0
            }
        }

    val snrDb: Double
        get() {
            val scrubbed = snrDbRaw.replace(Regex("(?i)dB"), "").trim()
            return try {
                scrubbed.toDouble()
            } catch (e: NumberFormatException) {
                MIN_SNR_DB
            }
        }

    fun isEmpty(): Boolean {
        return snrDbRaw.isEmpty() && snrRaw.isEmpty() && berRaw.isEmpty() && agcRaw.isEmpty()
    }

    companion object {
        const val MIN_SNR_DB = 5.0
    }
}
