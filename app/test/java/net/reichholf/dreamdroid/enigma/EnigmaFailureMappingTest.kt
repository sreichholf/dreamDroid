package net.reichholf.dreamdroid.enigma

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.Python
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnigmaFailureMappingTest {
    @Test
    fun socketTimeoutIsUnreachableTimeoutNotCancelled() {
        val failure = EnigmaFailure.fromThrowable(SocketTimeoutException("read timed out"))
        val unreachable = assertUnreachable(failure)
        assertEquals(EnigmaFailure.UnreachableReason.Timeout, unreachable.reason)
    }

    @Test
    fun socketTimeoutInCauseChainIsTimeoutFirst() {
        val timeout = SocketTimeoutException("read timed out")
        val wrapped = IOException("okhttp").apply { initCause(timeout) }
        val unreachable = assertUnreachable(EnigmaFailure.fromThrowable(wrapped))
        assertEquals(EnigmaFailure.UnreachableReason.Timeout, unreachable.reason)
    }

    @Test
    fun otherInterruptedIoIsCancelled() {
        val failure = EnigmaFailure.fromThrowable(InterruptedIOException("sleep interrupted"))
        assertEquals(EnigmaFailure.Cancelled, failure)
    }

    @Test
    fun dnsIsUnreachableDns() {
        val unreachable =
            assertUnreachable(EnigmaFailure.fromThrowable(UnknownHostException("box.local")))
        assertEquals(EnigmaFailure.UnreachableReason.Dns, unreachable.reason)
    }

    @Test
    fun connectIsUnreachableConnect() {
        val unreachable =
            assertUnreachable(EnigmaFailure.fromThrowable(ConnectException("Connection refused")))
        assertEquals(EnigmaFailure.UnreachableReason.Connect, unreachable.reason)
    }

    @Test
    fun unexpectedUrlArgumentIsIllegalHost() {
        val unreachable =
            assertUnreachable(
                EnigmaFailure.fromThrowable(IllegalArgumentException("unexpected url: http://["))
            )
        assertEquals(EnigmaFailure.UnreachableReason.IllegalHost, unreachable.reason)
    }

    @Test
    fun sslHandshakeIsUnreachableSsl() {
        val unreachable =
            assertUnreachable(EnigmaFailure.fromThrowable(SSLHandshakeException("untrusted")))
        assertEquals(EnigmaFailure.UnreachableReason.Ssl, unreachable.reason)
    }

    @Test
    fun sslExceptionIsUnreachableSsl() {
        val unreachable = assertUnreachable(EnigmaFailure.fromThrowable(SSLException("tls")))
        assertEquals(EnigmaFailure.UnreachableReason.Ssl, unreachable.reason)
    }

    @Test
    fun certificateExceptionInCauseIsSsl() {
        val wrapped = IOException("ssl").apply { initCause(CertificateException("expired")) }
        val unreachable = assertUnreachable(EnigmaFailure.fromThrowable(wrapped))
        assertEquals(EnigmaFailure.UnreachableReason.Ssl, unreachable.reason)
    }

    @Test
    fun http401IsAuth() {
        assertEquals(EnigmaFailure.Auth, EnigmaFailure.fromHttpStatus(401, "Unauthorized"))
    }

    @Test
    fun http500IsHttp() {
        val failure = EnigmaFailure.fromHttpStatus(500, "Server Error")
        assertTrue(failure is EnigmaFailure.Http)
        assertEquals(500, (failure as EnigmaFailure.Http).code)
        assertEquals("Server Error", failure.message)
    }

    @Test
    fun unknownKeepsLocalizedMessage() {
        val failure = EnigmaFailure.fromThrowable(IOException("boom"))
        assertTrue(failure is EnigmaFailure.Unknown)
        assertEquals("boom", (failure as EnigmaFailure.Unknown).detail)
    }

    @Test
    fun simpleResultHttpFailurePassesThrough() {
        val error = EnigmaHttpError(EnigmaFailure.Auth)
        val outcome =
            simpleResultFromFetch(EnigmaHttpResult.Failure(error)) { error("unused") }
        assertEquals(false, outcome.first)
        assertNull(outcome.second.stateText)
        assertSame(error, outcome.third)
    }

    @Test
    fun simpleResultFalseStateIsBoxRejectedWithoutChangingSuccess() {
        val xml = EnigmaHttpResult.Success("xml".toByteArray())
        val outcome =
            simpleResultFromFetch(xml) {
                SimpleResult(state = Python.FALSE, stateText = "Timer conflict")
            }
        assertEquals(true, outcome.first)
        assertEquals(Python.FALSE, outcome.second.state)
        assertEquals("Timer conflict", outcome.second.stateText)
        assertEquals(
            EnigmaFailure.BoxRejected("Timer conflict"),
            outcome.third!!.failure
        )
    }

    @Test
    fun simpleResultTrueStateHasNoError() {
        val xml = EnigmaHttpResult.Success("xml".toByteArray())
        val outcome =
            simpleResultFromFetch(xml) {
                SimpleResult(state = Python.TRUE, stateText = "OK")
            }
        assertEquals(true, outcome.first)
        assertNull(outcome.third)
    }

    @Test
    fun unreachableExceptionIsUnreachableEnigmaFailure() {
        val thrown =
            EnigmaFailureException(
                EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Dns)
            )
        assertTrue(thrown.isUnreachableEnigmaFailure())
        assertTrue(UnknownHostException("box.local").isUnreachableEnigmaFailure())
        assertFalse(EnigmaFailureException(EnigmaFailure.Auth).isUnreachableEnigmaFailure())
        assertFalse(IOException("boom").isUnreachableEnigmaFailure())
    }

    @Test
    fun valueOrThrowHttpFailure() {
        val response =
            EnigmaResponse<List<String>>(
                null,
                EnigmaHttpError(EnigmaFailure.Http(500, "Server Error"))
            )
        val thrown =
            assertThrows(EnigmaFailureException::class.java) { response.valueOrThrow() }
        assertEquals(EnigmaFailure.Http(500, "Server Error"), thrown.failure)
    }

    @Test
    fun valueOrThrowNullValueWithoutErrorIsParse() {
        val thrown =
            assertThrows(EnigmaFailureException::class.java) {
                EnigmaResponse<String>(null).valueOrThrow()
            }
        assertEquals(EnigmaFailure.Parse, thrown.failure)
    }

    private fun assertUnreachable(failure: EnigmaFailure): EnigmaFailure.Unreachable {
        assertTrue(failure is EnigmaFailure.Unreachable)
        return failure as EnigmaFailure.Unreachable
    }
}
