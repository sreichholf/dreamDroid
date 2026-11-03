package net.reichholf.dreamdroid.helpers

import java.io.OutputStream
import java.time.Duration
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFileFilter

/**
 * [PiconFtp] over the Apache commons-net client. Picons go over as binary, and the listing
 * is filtered to the `.png` files the receiver stores its picons as.
 *
 * The client's timeouts are set before [connect]: a receiver that stops answering must fail
 * the sync instead of leaving the worker blocked on an open socket.
 */
class CommonsNetPiconFtp(private val client: FTPClient = FTPClient()) : PiconFtp {
    init {
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.defaultTimeout = DEFAULT_TIMEOUT_MS
        client.setDataTimeout(Duration.ofMillis(DATA_TIMEOUT_MS.toLong()))
    }

    override fun connect(host: String) {
        client.connect(host)
    }

    override fun login(user: String?, pass: String?): FtpReply {
        val reply = reply(client.login(user, pass))
        if (reply.ok) {
            client.setFileType(FTPClient.BINARY_FILE_TYPE)
        }
        return reply
    }

    override fun changeDirectory(path: String): FtpReply =
        reply(client.changeWorkingDirectory(path))

    override fun listPicons(): List<String> =
        client.listFiles(null, PNG_FILES)?.map { it.name } ?: emptyList()

    override fun retrieve(name: String, to: OutputStream): FtpReply =
        reply(client.retrieveFile(name, to))

    override fun disconnect() {
        if (client.isConnected) {
            client.disconnect()
        }
    }

    private fun reply(ok: Boolean): FtpReply = FtpReply(ok, client.replyCode, client.replyString)

    private companion object {
        /**
         * Bound one socket wait. A receiver that accepts the connection and then stops
         * answering must fail the sync instead of blocking the worker. These are not tied
         * to the foreground service: it keeps the worker alive, and its own limit is hours.
         */
        const val CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_TIMEOUT_MS = 30_000
        const val DATA_TIMEOUT_MS = 30_000

        val PNG_FILES = FTPFileFilter { file -> file.isFile && file.name.endsWith(".png") }
    }
}
