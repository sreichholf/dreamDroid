package net.reichholf.dreamdroid.helpers

import java.io.OutputStream

/** One FTP answer: whether the step succeeded, and what the server said about it. */
data class FtpReply(val ok: Boolean, val code: Int, val text: String?)

/**
 * What a picon sync needs from a receiver's FTP server. The sync never sees an FTP client,
 * so it runs against a fake in tests and the library stays in [CommonsNetPiconFtp].
 */
interface PiconFtp {
    fun connect(host: String)

    fun login(user: String?, pass: String?): FtpReply

    fun changeDirectory(path: String): FtpReply

    /** The `.png` files in the current directory. */
    fun listPicons(): List<String>

    fun retrieve(name: String, to: OutputStream): FtpReply

    /** Ends the session; safe to call when nothing was opened. */
    fun disconnect()
}
