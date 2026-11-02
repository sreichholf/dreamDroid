package net.reichholf.dreamdroid.helpers

import java.io.OutputStream
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFileFilter

/**
 * [PiconFtp] over the Apache commons-net client. Picons go over as binary, and the listing
 * is filtered to the `.png` files the receiver stores its picons as.
 */
class CommonsNetPiconFtp(private val client: FTPClient = FTPClient()) : PiconFtp {
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
        val PNG_FILES = FTPFileFilter { file -> file.isFile && file.name.endsWith(".png") }
    }
}
