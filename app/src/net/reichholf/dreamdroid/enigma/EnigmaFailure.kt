package net.reichholf.dreamdroid.enigma

import android.content.Context
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.text.asString

/**
 * Typed Enigma2 failure. HTTP mapping lives in [fromThrowable] / [fromHttpStatus];
 * [BoxRejected] is produced by SimpleResult handlers, not [helpers.EnigmaHttp.fetch].
 */
sealed class EnigmaFailure {
    enum class UnreachableReason {
        Dns,
        Connect,
        Timeout,
        Ssl,
        IllegalHost
    }

    data class Unreachable(val reason: UnreachableReason, val detail: String? = null) :
        EnigmaFailure()

    data object Auth : EnigmaFailure()

    data class Http(val code: Int, val message: String? = null) : EnigmaFailure()

    data object Parse : EnigmaFailure()

    data class BoxRejected(val stateText: String) : EnigmaFailure()

    data object Cancelled : EnigmaFailure()

    data class Unknown(val detail: String? = null) : EnigmaFailure()

    fun userMessage(context: Context): String = userMessageText().asString(context.resources)

    fun userMessageText(): UiText = when (this) {
        is Unreachable ->
            UiText.Resource(
                when (reason) {
                    UnreachableReason.Dns -> R.string.host_not_found

                    UnreachableReason.Connect,
                    UnreachableReason.Timeout,
                    UnreachableReason.Ssl -> R.string.host_unreach

                    UnreachableReason.IllegalHost -> R.string.illegal_host
                }
            )

        is Auth -> UiText.Resource(R.string.auth_error)

        is Http -> UiText.Raw(message.orEmpty())

        is Parse -> UiText.Resource(R.string.error_parsing)

        is BoxRejected -> UiText.Raw(stateText)

        is Cancelled -> UiText.Raw("")

        is Unknown -> UiText.Raw(detail.orEmpty())
    }

    companion object {
        fun fromThrowable(throwable: Throwable): EnigmaFailure {
            val chain = causeChain(throwable)
            firstOf<SocketTimeoutException>(chain)?.let { timeout ->
                return Unreachable(UnreachableReason.Timeout, timeout.localizedMessage)
            }
            if (chain.any { it is InterruptedIOException || it is InterruptedException }) {
                return Cancelled
            }
            firstOf<MalformedURLException>(chain)?.let { badHost ->
                return Unreachable(UnreachableReason.IllegalHost, badHost.localizedMessage)
            }
            illegalHostArgument(chain)?.let { badUrl ->
                return Unreachable(UnreachableReason.IllegalHost, badUrl.localizedMessage)
            }
            firstOf<UnknownHostException>(chain)?.let { dns ->
                return Unreachable(UnreachableReason.Dns, dns.localizedMessage)
            }
            firstOf<ConnectException>(chain)?.let { connect ->
                return Unreachable(UnreachableReason.Connect, connect.localizedMessage)
            }
            sslOf(chain)?.let { ssl ->
                return Unreachable(UnreachableReason.Ssl, ssl.localizedMessage)
            }
            return Unknown(throwable.localizedMessage)
        }

        fun fromHttpStatus(code: Int, message: String?): EnigmaFailure =
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Auth
            } else {
                Http(code, message)
            }
    }
}

/** Thrown when MultiEPG (and similar) must fail a fetch with a typed [EnigmaFailure]. */
class EnigmaFailureException(val failure: EnigmaFailure) : Exception()

/** DNS / connect / timeout / SSL belong on the session chip, not in-content copy. */
fun Throwable.isUnreachableEnigmaFailure(): Boolean {
    val failure =
        (this as? EnigmaFailureException)?.failure ?: EnigmaFailure.fromThrowable(this)
    return failure is EnigmaFailure.Unreachable
}

fun Throwable.toEnigmaDisplayMessage(context: Context): String {
    if (this is EnigmaFailureException) {
        return failure.userMessage(context)
    }
    return message ?: javaClass.simpleName
}

private fun causeChain(throwable: Throwable): List<Throwable> {
    val out = ArrayList<Throwable>()
    val seen = HashSet<Throwable>()
    var current: Throwable? = throwable
    while (current != null && seen.add(current)) {
        out.add(current)
        current = current.cause
    }
    return out
}

private inline fun <reified T : Throwable> firstOf(chain: List<Throwable>): T? =
    chain.filterIsInstance<T>().firstOrNull()

private fun sslOf(chain: List<Throwable>): Throwable? =
    chain.firstOrNull { it is SSLException || it is CertificateException }

private fun illegalHostArgument(chain: List<Throwable>): IllegalArgumentException? =
    chain.filterIsInstance<IllegalArgumentException>().firstOrNull { error ->
        error.message?.contains("unexpected url", ignoreCase = true) == true
    }
