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
    private var mProfile: Profile? = null
    private var mPrefix: String = "http://"
    private var mFilePrefix: String = "http://"
    private var mBytes: ByteArray = ByteArray(0)
    private var mErrorText: String? = null
    private var mErrorTextId: Int = -1
    private var mError: Boolean = false
    private var mRememberedReturnCode: Int = 0
    private var mConnectionTimeoutMillis: Int = 3000
    private var okHttpClient: OkHttpClient? = null
    private var okHttpTimeoutMillis: Int = -1
    private var okHttpSsl: Boolean? = null
    private var okHttpTrustAll: Boolean? = null

    @Volatile
    private var inFlight: Call? = null
    private val fetchEpoch = AtomicInteger(0)

    constructor() {
        mProfile = null
        init()
    }

    constructor(p: Profile?) {
        mProfile = p
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
        return mPrefix + mProfile!!.host + ":" + mProfile!!.port + path + parms
    }

    fun buildAuthedUrl(uri: String, parameters: List<NameValuePair>): String {
        var path = uri
        val parms = NameValuePair.toString(parameters)
        if (!path.contains("?")) {
            path += "?"
        }
        var loginString = ""
        if (mProfile!!.login) {
            loginString = String.format("%s:%s@", mProfile!!.user, mProfile!!.pass)
        }
        return mPrefix + loginString + mProfile!!.host + ":" + mProfile!!.port + path + parms
    }

    fun buildEncoderStreamUrl(ref: String): String {
        if (mProfile!!.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        var encoded = ref
        try {
            encoded = URLEncoder.encode(ref, "utf-8").replace("+", "%20")
        } catch (_: UnsupportedEncodingException) {
        }
        var streamLoginString = ""
        if (mProfile!!.encoderLogin) {
            streamLoginString = mProfile!!.encoderUser + ":" + mProfile!!.encoderPass + "@"
        }
        return String.format(
            "rtsp://%s%s:%s/%s?ref=%s&video_bitrate=%s&audio_bitrate=%s",
            streamLoginString,
            mProfile!!.streamHostOrHost,
            mProfile!!.encoderPort,
            mProfile!!.encoderPath,
            encoded,
            mProfile!!.encoderVideoBitrate,
            mProfile!!.encoderAudioBitrate
        )
    }

    fun buildStreamUrl(ref: String): String {
        if (mProfile!!.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        return if (mProfile!!.encoderStream) {
            buildEncoderStreamUrl(ref)
        } else {
            buildServiceStreamUrl(ref)
        }
    }

    fun buildServiceStreamUrl(ref: String): String {
        if (mProfile!!.host == "dreamdroid.org") {
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
            enabled = mProfile!!.streamLogin,
            user = mProfile!!.user,
            pass = mProfile!!.pass,
            scheme = "http"
        )
        return "http://" + streamLoginString + mProfile!!.streamHostOrHost + ":" +
            mProfile!!.streamPort + "/" + serviceRef
    }

    fun buildFileStreamUrl(ref: String, fileName: String?): String {
        if (mProfile!!.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        if (mProfile!!.encoderStream && ref.startsWith("1:")) {
            return buildEncoderStreamUrl(ref)
        }
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("file", fileName))
        val parms = NameValuePair.toString(params)
        val fileScheme = if (mProfile!!.fileSsl) "https" else "http"
        val fileAuthString = HttpUserInfo.embed(
            enabled = mProfile!!.fileLogin,
            user = mProfile!!.user,
            pass = mProfile!!.pass,
            scheme = fileScheme
        )
        return mFilePrefix + fileAuthString + mProfile!!.streamHostOrHost + ":" +
            mProfile!!.filePort + URIStore.FILE + parms
    }

    fun fetchPageContent(uri: String): Boolean = fetchPageContent(uri, ArrayList())

    private fun isSessionLess(uri: String): Boolean = URIStore.SCREENSHOT == uri

    private fun authHeader(): String? {
        if (!mProfile!!.login) return null
        return Credentials.basic(mProfile!!.user.orEmpty(), mProfile!!.pass.orEmpty())
    }

    private fun newClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(mConnectionTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(mConnectionTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(mConnectionTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
        val appContext = DreamDroid.getAppContext()
        if (appContext != null) {
            try {
                val trustAll = mProfile?.allCertsTrusted == true
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
        val ssl = mProfile?.ssl == true
        val trustAll = mProfile?.allCertsTrusted == true
        val cached = okHttpClient
        if (
            cached != null &&
            okHttpTimeoutMillis == mConnectionTimeoutMillis &&
            okHttpSsl == ssl &&
            okHttpTrustAll == trustAll
        ) {
            return cached
        }
        val created = newClient()
        okHttpClient = created
        okHttpTimeoutMillis = mConnectionTimeoutMillis
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

        mErrorText = ""
        mErrorTextId = -1
        mError = false
        mBytes = ByteArray(0)
        var path = uri
        if (!path.startsWith("/")) {
            path = "/$path"
        }

        var call: Call? = null
        try {
            val requestParams = ArrayList(parameters)
            if (mProfile!!.sessionId != null && !isSessionLess(path)) {
                requestParams.add(NameValuePair("sessionid", mProfile!!.sessionId))
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
            mError = true
            mErrorTextId = R.string.illegal_host
        } catch (e: UnknownHostException) {
            if (epoch != fetchEpoch.get()) return false
            mError = true
            mErrorText = null
            mErrorTextId = R.string.host_not_found
        } catch (e: ProtocolException) {
            if (epoch != fetchEpoch.get()) return false
            mError = true
            mErrorText = e.localizedMessage
        } catch (e: ConnectException) {
            if (epoch != fetchEpoch.get()) return false
            mError = true
            mErrorTextId = R.string.host_unreach
        } catch (e: IOException) {
            if (epoch != fetchEpoch.get()) return false
            when (val cause = e.cause) {
                is UnknownHostException -> {
                    mError = true
                    mErrorText = null
                    mErrorTextId = R.string.host_not_found
                }

                is ConnectException -> {
                    mError = true
                    mErrorTextId = R.string.host_unreach
                }

                else -> {
                    e.printStackTrace()
                    mError = true
                    mErrorText = e.localizedMessage
                }
            }
        } catch (e: NullPointerException) {
            if (epoch != fetchEpoch.get()) return false
            e.printStackTrace()
            mError = true
            mErrorText = e.localizedMessage
        } finally {
            val finished = call
            if (finished != null && inFlight === finished) {
                inFlight = null
            }
            if (mError && epoch == fetchEpoch.get()) {
                if (mErrorText == null) {
                    mErrorText = "Error text is null"
                }
                Log.e(LOG_TAG, mErrorText ?: "Error text is null")
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
                mRememberedReturnCode != HttpURLConnection.HTTP_BAD_METHOD
            ) {
                DreamDroid.setFeaturePostRequest(!DreamDroid.featurePostRequest())
                mRememberedReturnCode = HttpURLConnection.HTTP_BAD_METHOD
                return fetchPageContent(uri, parameters)
            }
            if (code == HttpURLConnection.HTTP_PRECON_FAILED &&
                mRememberedReturnCode != HttpURLConnection.HTTP_PRECON_FAILED
            ) {
                createSession()
                mRememberedReturnCode = HttpURLConnection.HTTP_PRECON_FAILED
                return fetchPageContent(uri, parameters)
            }
            if (epoch != fetchEpoch.get()) return false
            mRememberedReturnCode = 0
            Log.e(LOG_TAG, code.toString())
            when (code) {
                HttpURLConnection.HTTP_UNAUTHORIZED -> mErrorTextId = R.string.auth_error
                else -> mErrorTextId = -1
            }
            mErrorText = response.message
            mError = true
            return false
        }
        val body = response.body?.bytes() ?: ByteArray(0)
        if (epoch != fetchEpoch.get()) return false
        mBytes = body
        if (DreamDroid.dumpXml()) {
            dumpToFile(urlString)
        }
        return true
    }

    private fun createSession() {
        val shc = getInstance(mProfile)
        shc.fetchPageContent(URIStore.SESSION)
        if (!shc.hasError()) {
            var content = shc.pageContentString
            content = content.replace(Regex("\\<.*?\\>"), "").trim()
            mProfile!!.sessionId = content
        } else {
            mProfile!!.sessionId = null
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
                bos.write(mBytes)
                bos.flush()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val pageContentString: String
        get() = String(mBytes)

    /** Also exposed to Kotlin as `.bytes` (Request.getBytes). */
    val bytes: ByteArray
        get() = mBytes

    fun getErrorText(context: Context): String? {
        if (mErrorTextId > 0) {
            return context.getString(mErrorTextId)
        }
        return mErrorText
    }

    fun hasError(): Boolean = mError

    fun applyConfig() {
        if (mProfile == null) {
            mProfile = DreamDroid.getCurrentProfile()
        }
        mPrefix = if (mProfile!!.ssl) "https://" else "http://"
        mFilePrefix = if (mProfile!!.fileSsl) "https://" else "http://"
    }

    fun setConnectionTimeoutMillis(millis: Int) {
        mConnectionTimeoutMillis = millis
    }

    companion object {
        val LOG_TAG: String = SimpleHttpClient::class.java.simpleName

        const val BIG_BUCK_BUNNY_URL: String =
            "https://dreamdroid.org/bunny/big_buck_bunny_720p_h264.mov"

        fun getInstance(): SimpleHttpClient = SimpleHttpClient()

        fun getInstance(p: Profile?): SimpleHttpClient = SimpleHttpClient(p)
    }
}
