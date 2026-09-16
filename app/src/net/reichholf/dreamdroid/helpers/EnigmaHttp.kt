package net.reichholf.dreamdroid.helpers

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class EnigmaHttpError(val message: String? = null, val messageId: Int = -1) {
    fun resolve(context: Context): String? {
        if (messageId > 0) {
            return context.getString(messageId)
        }
        return message
    }
}

sealed class EnigmaHttpResult {
    data class Success(val bytes: ByteArray) : EnigmaHttpResult() {
        val text: String
            get() = String(bytes)
    }

    data class Failure(val error: EnigmaHttpError) : EnigmaHttpResult()
}

/**
 * Per-request Enigma2 HTTP. Share [EnigmaOkHttp] under the hood; do not share
 * this type across concurrent fetches (a second [fetch] cancels the first).
 */
class EnigmaHttp(profile: Profile? = null, timeoutMillis: Int = DEFAULT_CONNECTION_TIMEOUT_MILLIS) {
    private val profile: Profile = profile ?: DreamDroid.getCurrentProfile()
    private var timeoutMillis: Int = timeoutMillis
    private var rememberedReturnCode: Int = 0

    @Volatile
    private var inFlight: Call? = null
    private val fetchEpoch = AtomicInteger(0)

    fun setConnectionTimeoutMillis(millis: Int) {
        timeoutMillis = millis
    }

    fun connectionTimeoutMillis(): Int = timeoutMillis

    fun fetch(uri: String, parameters: List<NameValuePair> = emptyList()): EnigmaHttpResult {
        inFlight?.cancel()
        val epoch = fetchEpoch.incrementAndGet()
        var path = uri
        if (!path.startsWith("/")) {
            path = "/$path"
        }

        var call: Call? = null
        var failure: EnigmaHttpError? = null
        try {
            val requestParams = ArrayList(parameters)
            if (profile.sessionId != null && !isSessionLess(path)) {
                requestParams.add(NameValuePair("sessionid", profile.sessionId))
            }
            val urlString = EnigmaUrls.page(profile, path, requestParams)
            val requestBuilder = Request.Builder().url(urlString)
            authHeader()?.let { requestBuilder.header("Authorization", it) }
            if (DreamDroid.featurePostRequest()) {
                requestBuilder.post(ByteArray(0).toRequestBody(null))
            } else {
                requestBuilder.get()
            }

            call = httpClient().newCall(requestBuilder.build())
            inFlight = call
            if (Thread.currentThread().isInterrupted) {
                call.cancel()
                throw InterruptedIOException()
            }
            executeInterruptibly(call).use { response ->
                return handleResponse(path, parameters, urlString, response, epoch)
            }
        } catch (e: MalformedURLException) {
            failure = EnigmaHttpError(messageId = R.string.illegal_host)
        } catch (e: UnknownHostException) {
            failure = EnigmaHttpError(messageId = R.string.host_not_found)
        } catch (e: ProtocolException) {
            failure = EnigmaHttpError(message = e.localizedMessage)
        } catch (e: ConnectException) {
            failure = EnigmaHttpError(messageId = R.string.host_unreach)
        } catch (e: IOException) {
            failure = when (val cause = e.cause) {
                is UnknownHostException -> EnigmaHttpError(messageId = R.string.host_not_found)

                is ConnectException -> EnigmaHttpError(messageId = R.string.host_unreach)

                else -> {
                    e.printStackTrace()
                    EnigmaHttpError(message = e.localizedMessage)
                }
            }
        } catch (e: NullPointerException) {
            e.printStackTrace()
            failure = EnigmaHttpError(message = e.localizedMessage)
        } finally {
            val finished = call
            if (finished != null && inFlight === finished) {
                inFlight = null
            }
        }
        if (epoch != fetchEpoch.get()) {
            return EnigmaHttpResult.Failure(EnigmaHttpError())
        }
        val error = failure ?: EnigmaHttpError(message = "Error text is null")
        if (error.message == null && error.messageId <= 0) {
            Log.e(LOG_TAG, "Error text is null")
        } else {
            Log.e(LOG_TAG, error.message ?: "Error text is null")
        }
        return EnigmaHttpResult.Failure(error)
    }

    private fun handleResponse(
        uri: String,
        parameters: List<NameValuePair>,
        urlString: String,
        response: Response,
        epoch: Int
    ): EnigmaHttpResult {
        val code = response.code
        if (code != HttpURLConnection.HTTP_OK) {
            if (code == HttpURLConnection.HTTP_BAD_METHOD &&
                rememberedReturnCode != HttpURLConnection.HTTP_BAD_METHOD
            ) {
                DreamDroid.setFeaturePostRequest(!DreamDroid.featurePostRequest())
                rememberedReturnCode = HttpURLConnection.HTTP_BAD_METHOD
                return fetch(uri, parameters)
            }
            if (code == HttpURLConnection.HTTP_PRECON_FAILED &&
                rememberedReturnCode != HttpURLConnection.HTTP_PRECON_FAILED
            ) {
                createSession()
                rememberedReturnCode = HttpURLConnection.HTTP_PRECON_FAILED
                return fetch(uri, parameters)
            }
            if (epoch != fetchEpoch.get()) {
                return EnigmaHttpResult.Failure(EnigmaHttpError())
            }
            rememberedReturnCode = 0
            Log.e(LOG_TAG, code.toString())
            val messageId = if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                R.string.auth_error
            } else {
                -1
            }
            return EnigmaHttpResult.Failure(
                EnigmaHttpError(message = response.message, messageId = messageId)
            )
        }
        val body = response.body?.bytes() ?: ByteArray(0)
        if (epoch != fetchEpoch.get()) {
            return EnigmaHttpResult.Failure(EnigmaHttpError())
        }
        if (DreamDroid.dumpXml()) {
            dumpToFile(urlString, body)
        }
        return EnigmaHttpResult.Success(body)
    }

    private fun createSession() {
        val sessionHttp = EnigmaHttp(profile, timeoutMillis)
        when (val result = sessionHttp.fetch(URIStore.SESSION)) {
            is EnigmaHttpResult.Success -> {
                val content = result.text.replace(Regex("\\<.*?\\>"), "").trim()
                profile.sessionId = content
            }

            is EnigmaHttpResult.Failure -> profile.sessionId = null
        }
    }

    private fun dumpToFile(urlString: String, bytes: ByteArray) {
        val context = DreamDroid.getAppContext() ?: return
        val dumpDir = File(context.cacheDir, "xml")
        val parts = urlString.split("/")
        val fn = parts[parts.size - 1].split("\\?".toRegex()).toTypedArray()[0]
        Log.w("--------------", fn)
        val file = File(dumpDir, fn)
        try {
            dumpDir.mkdirs()
            file.createNewFile()
            BufferedOutputStream(FileOutputStream(file)).use { bos ->
                bos.write(bytes)
                bos.flush()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun isSessionLess(uri: String): Boolean = URIStore.SCREENSHOT == uri

    private fun authHeader(): String? {
        if (!profile.login) return null
        return Credentials.basic(profile.user.orEmpty(), profile.pass.orEmpty())
    }

    private fun httpClient() = EnigmaOkHttp.client(timeoutMillis, profile.allCertsTrusted)

    private fun executeInterruptibly(call: Call): Response {
        val responseRef = AtomicReference<Response>()
        val errorRef = AtomicReference<IOException>()
        val done = CountDownLatch(1)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    errorRef.set(e)
                    done.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    responseRef.set(response)
                    done.countDown()
                }
            }
        )
        try {
            done.await()
        } catch (e: InterruptedException) {
            call.cancel()
            Thread.currentThread().interrupt()
            val interrupted = InterruptedIOException()
            interrupted.initCause(e)
            throw interrupted
        }
        val error = errorRef.get()
        if (error != null) {
            throw error
        }
        return responseRef.get()
    }

    companion object {
        val LOG_TAG: String = EnigmaHttp::class.java.simpleName

        /**
         * Connect, read, and write timeout for Enigma2 HTTP.
         *
         * Movie lists on a spinning HDD can stall several seconds while the disc
         * spins up and the box reads the recording index.
         */
        const val DEFAULT_CONNECTION_TIMEOUT_MILLIS: Int = 15_000
    }
}
