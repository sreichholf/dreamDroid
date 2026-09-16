/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.UnsupportedEncodingException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Enigma2 HTTP client. OkHttp (aligned with Coil for picons) instead of
 * [HttpURLConnection]. Public API unchanged for callers.
 */
class SimpleHttpClient {
    private var profile: Profile? = null
    private var prefix: String = "http://"
    private var filePrefix: String = "http://"

    var bytes: ByteArray = ByteArray(0)
        private set
    private var errorText: String? = null
    private var errorTextId: Int = -1
    private var error: Boolean = false
    private var rememberedReturnCode: Int = 0
    private var timeoutMillis: Int = DEFAULT_CONNECTION_TIMEOUT_MILLIS

    private var okHttpClient: OkHttpClient? = null
    private var okHttpTimeoutMillis: Int = -1
    private var okHttpSsl: Boolean? = null
    private var okHttpTrustAll: Boolean? = null

    @Volatile
    private var inFlight: Call? = null
    private val fetchEpoch = AtomicInteger(0)

    constructor() {
        profile = null
        init()
    }

    constructor(p: Profile?) {
        profile = p
        init()
    }

    private fun init() {
        applyConfig()
    }

    fun buildUrl(uri: String, parameters: List<NameValuePair>): String {
        var path = uri
        val parms = NameValuePair.toString(parameters)
        if (!path.contains("?")) {
            path += "?"
        }
        return prefix + profile!!.host + ":" + profile!!.port + path + parms
    }

    fun buildAuthedUrl(uri: String, parameters: List<NameValuePair>): String {
        var path = uri
        val parms = NameValuePair.toString(parameters)
        if (!path.contains("?")) {
            path += "?"
        }
        var loginString = ""
        if (profile!!.login) {
            loginString = String.format("%s:%s@", profile!!.user, profile!!.pass)
        }
        return prefix + loginString + profile!!.host + ":" + profile!!.port + path + parms
    }

    fun buildEncoderStreamUrl(ref: String): String {
        if (profile!!.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        var encoded = ref
        try {
            encoded = URLEncoder.encode(ref, "utf-8").replace("+", "%20")
        } catch (_: UnsupportedEncodingException) {
        }
        var streamLoginString = ""
        if (profile!!.encoderLogin) {
            streamLoginString = profile!!.encoderUser + ":" + profile!!.encoderPass + "@"
        }
        return String.format(
            "rtsp://%s%s:%s/%s?ref=%s&video_bitrate=%s&audio_bitrate=%s",
            streamLoginString,
            profile!!.streamHostOrHost,
            profile!!.encoderPort,
            profile!!.encoderPath,
            encoded,
            profile!!.encoderVideoBitrate,
            profile!!.encoderAudioBitrate
        )
    }

    fun buildStreamUrl(ref: String): String {
        if (profile!!.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        return if (profile!!.encoderStream) {
            buildEncoderStreamUrl(ref)
        } else {
            buildServiceStreamUrl(ref)
        }
    }

    fun buildServiceStreamUrl(ref: String): String {
        if (profile!!.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        var serviceRef = ref
        if (serviceRef.contains("http")) {
            try {
                return URLDecoder.decode(
                    serviceRef.substring(serviceRef.indexOf("http")),
                    "utf-8"
                ).replace(" ", "%20")
            } catch (_: UnsupportedEncodingException) {
            }
        }
        try {
            serviceRef = URLEncoder.encode(serviceRef, "utf-8").replace("+", "%20")
        } catch (_: UnsupportedEncodingException) {
        }
        val streamLoginString = HttpUserInfo.embed(
            enabled = profile!!.streamLogin,
            user = profile!!.user,
            pass = profile!!.pass,
            scheme = "http"
        )
        return "http://" + streamLoginString + profile!!.streamHostOrHost + ":" +
            profile!!.streamPort + "/" + serviceRef
    }

    fun buildFileStreamUrl(ref: String, fileName: String?): String {
        if (profile!!.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        if (profile!!.encoderStream && ref.startsWith("1:")) {
            return buildEncoderStreamUrl(ref)
        }
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("file", fileName))
        val parms = NameValuePair.toString(params)
        val fileScheme = if (profile!!.fileSsl) "https" else "http"
        val fileAuthString = HttpUserInfo.embed(
            enabled = profile!!.fileLogin,
            user = profile!!.user,
            pass = profile!!.pass,
            scheme = fileScheme
        )
        return filePrefix + fileAuthString + profile!!.streamHostOrHost + ":" +
            profile!!.filePort + URIStore.FILE + parms
    }

    fun fetchPageContent(uri: String): Boolean = fetchPageContent(uri, ArrayList())

    private fun isSessionLess(uri: String): Boolean = URIStore.SCREENSHOT == uri

    private fun authHeader(): String? {
        if (!profile!!.login) return null
        return Credentials.basic(profile!!.user.orEmpty(), profile!!.pass.orEmpty())
    }

    private fun newClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
        val appContext = DreamDroid.getAppContext()
        if (appContext != null) {
            try {
                val trustAll = profile?.allCertsTrusted == true
                val trustManager = DreamDroidTrustManager(appContext, trustAll)
                val sc = SSLContext.getInstance("TLS")
                sc.init(null, arrayOf<X509TrustManager>(trustManager), SecureRandom())
                builder.sslSocketFactory(sc.socketFactory, trustManager)
                builder.hostnameVerifier(
                    trustManager.wrapHostnameVerifier(
                        HttpsURLConnection.getDefaultHostnameVerifier()
                    )
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "SSL setup for OkHttp failed", e)
            }
        }
        return builder.build()
    }

    private fun httpClient(): OkHttpClient {
        val ssl = profile?.ssl == true
        val trustAll = profile?.allCertsTrusted == true
        val cached = okHttpClient
        if (
            cached != null &&
            okHttpTimeoutMillis == timeoutMillis &&
            okHttpSsl == ssl &&
            okHttpTrustAll == trustAll
        ) {
            return cached
        }
        val created = newClient()
        okHttpClient = created
        okHttpTimeoutMillis = timeoutMillis
        okHttpSsl = ssl
        okHttpTrustAll = trustAll
        return created
    }

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

    fun fetchPageContent(uri: String, parameters: MutableList<NameValuePair>): Boolean {
        applyConfig()
        inFlight?.cancel()
        val epoch = fetchEpoch.incrementAndGet()

        errorText = ""
        errorTextId = -1
        error = false
        bytes = ByteArray(0)
        var path = uri
        if (!path.startsWith("/")) {
            path = "/$path"
        }

        var call: Call? = null
        try {
            val requestParams = ArrayList(parameters)
            if (profile!!.sessionId != null && !isSessionLess(path)) {
                requestParams.add(NameValuePair("sessionid", profile!!.sessionId))
            }
            val urlString = buildUrl(path, requestParams)
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
            if (epoch != fetchEpoch.get()) return false
            error = true
            errorTextId = R.string.illegal_host
        } catch (e: UnknownHostException) {
            if (epoch != fetchEpoch.get()) return false
            error = true
            errorText = null
            errorTextId = R.string.host_not_found
        } catch (e: ProtocolException) {
            if (epoch != fetchEpoch.get()) return false
            error = true
            errorText = e.localizedMessage
        } catch (e: ConnectException) {
            if (epoch != fetchEpoch.get()) return false
            error = true
            errorTextId = R.string.host_unreach
        } catch (e: IOException) {
            if (epoch != fetchEpoch.get()) return false
            when (val cause = e.cause) {
                is UnknownHostException -> {
                    error = true
                    errorText = null
                    errorTextId = R.string.host_not_found
                }

                is ConnectException -> {
                    error = true
                    errorTextId = R.string.host_unreach
                }

                else -> {
                    e.printStackTrace()
                    error = true
                    errorText = e.localizedMessage
                }
            }
        } catch (e: NullPointerException) {
            if (epoch != fetchEpoch.get()) return false
            e.printStackTrace()
            error = true
            errorText = e.localizedMessage
        } finally {
            val finished = call
            if (finished != null && inFlight === finished) {
                inFlight = null
            }
            if (error && epoch == fetchEpoch.get()) {
                if (errorText == null) {
                    errorText = "Error text is null"
                }
                Log.e(LOG_TAG, errorText ?: "Error text is null")
            }
        }
        return false
    }

    private fun handleResponse(
        uri: String,
        parameters: MutableList<NameValuePair>,
        urlString: String,
        response: Response,
        epoch: Int
    ): Boolean {
        val code = response.code
        if (code != HttpURLConnection.HTTP_OK) {
            if (code == HttpURLConnection.HTTP_BAD_METHOD &&
                rememberedReturnCode != HttpURLConnection.HTTP_BAD_METHOD
            ) {
                DreamDroid.setFeaturePostRequest(!DreamDroid.featurePostRequest())
                rememberedReturnCode = HttpURLConnection.HTTP_BAD_METHOD
                return fetchPageContent(uri, parameters)
            }
            if (code == HttpURLConnection.HTTP_PRECON_FAILED &&
                rememberedReturnCode != HttpURLConnection.HTTP_PRECON_FAILED
            ) {
                createSession()
                rememberedReturnCode = HttpURLConnection.HTTP_PRECON_FAILED
                return fetchPageContent(uri, parameters)
            }
            if (epoch != fetchEpoch.get()) return false
            rememberedReturnCode = 0
            Log.e(LOG_TAG, code.toString())
            when (code) {
                HttpURLConnection.HTTP_UNAUTHORIZED -> errorTextId = R.string.auth_error
                else -> errorTextId = -1
            }
            errorText = response.message
            error = true
            return false
        }
        val body = response.body?.bytes() ?: ByteArray(0)
        if (epoch != fetchEpoch.get()) return false
        bytes = body
        if (DreamDroid.dumpXml()) {
            dumpToFile(urlString)
        }
        return true
    }

    private fun createSession() {
        val shc = getInstance(profile)
        shc.fetchPageContent(URIStore.SESSION)
        if (!shc.hasError()) {
            var content = shc.pageContentString
            content = content.replace(Regex("\\<.*?\\>"), "").trim()
            profile!!.sessionId = content
        } else {
            profile!!.sessionId = null
        }
    }

    private fun dumpToFile(urlString: String) {
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

    val pageContentString: String
        get() = String(bytes)

    fun getErrorText(context: Context): String? {
        if (errorTextId > 0) {
            return context.getString(errorTextId)
        }
        return errorText
    }

    fun hasError(): Boolean = error

    fun applyConfig() {
        if (profile == null) {
            profile = DreamDroid.getCurrentProfile()
        }
        prefix = if (profile!!.ssl) "https://" else "http://"
        filePrefix = if (profile!!.fileSsl) "https://" else "http://"
    }

    fun setConnectionTimeoutMillis(millis: Int) {
        timeoutMillis = millis
    }

    fun connectionTimeoutMillis(): Int = timeoutMillis

    companion object {
        val LOG_TAG: String = SimpleHttpClient::class.java.simpleName

        const val BIG_BUCK_BUNNY_URL: String =
            "https://dreamdroid.org/bunny/big_buck_bunny_720p_h264.mov"

        /**
         * Connect, read, and write timeout for Enigma2 HTTP.
         *
         * Movie lists on a spinning HDD can stall several seconds while the disc
         * spins up and the box reads the recording index.
         */
        const val DEFAULT_CONNECTION_TIMEOUT_MILLIS: Int = 15_000

        fun getInstance(): SimpleHttpClient = SimpleHttpClient()

        fun getInstance(p: Profile?): SimpleHttpClient = SimpleHttpClient(p)
    }
}
