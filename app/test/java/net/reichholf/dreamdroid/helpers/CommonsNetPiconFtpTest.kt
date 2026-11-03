package net.reichholf.dreamdroid.helpers

import java.time.Duration
import org.apache.commons.net.ftp.FTPClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The client must not wait forever: a receiver that stops answering has to fail the sync
 * instead of keeping the foreground worker blocked on a socket.
 */
class CommonsNetPiconFtpTest {
    @Test
    fun theClientGetsConnectControlAndDataTimeouts() {
        val client = FTPClient()

        CommonsNetPiconFtp(client)

        assertEquals(15_000, client.connectTimeout)
        assertEquals(30_000, client.defaultTimeout)
        assertEquals(Duration.ofSeconds(30), client.dataTimeout)
    }
}
