/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers

import android.content.Context
import android.os.Environment
import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Enigma2 HTTP client. Phase SOTA: OkHttp (aligned with Picasso) instead of
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
        return mPrefix + mProfile!!.getHost() + ":" + mProfile!!.getPortString() + path + parms
    }

    fun buildAuthedUrl(uri: String, parameters: List<NameValuePair>): String {
        var path = uri
        val parms = NameValuePair.toString(parameters)
        if (!path.contains("?")) {
            path += "?"
        }
        var loginString = ""
        if (mProfile!!.isLogin()) {
            loginString = String.format("%s:%s@", mProfile!!.getUser(), mProfile!!.getPass())
        }
        return mPrefix + loginString + mProfile!!.getHost() + ":" + mProfile!!.getPortString() + path + parms
    }

    fun buildEncoderStreamUrl(ref: String): String {
        if (mProfile!!.getHost() == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        var encoded = ref
        try {
            encoded = URLEncoder.encode(ref, "utf-8").replace("+", "%20")
        } catch (_: UnsupportedEncodingException) {
        }
        var streamLoginString = ""
        if (mProfile!!.isEncoderLogin()) {
            streamLoginString = mProfile!!.getEncoderUser() + ":" + mProfile!!.getEncoderPass() + "@"
        }
        return String.format(
            "rtsp://%s%s:%s/%s?ref=%s&video_bitrate=%s&audio_bitrate=%s",
            streamLoginString,
            mProfile!!.getStreamHost(),
            mProfile!!.getEncoderPort(),
            mProfile!!.getEncoderPath(),
            encoded,
            mProfile!!.getEncoderVideoBitrate(),
            mProfile!!.getEncoderAudioBitrate(),
        )
    }

    fun buildStreamUrl(ref: String): String {
        if (mProfile!!.getHost() == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        return if (mProfile!!.isEncoderStream()) {
            buildEncoderStreamUrl(ref)
        } else {
            buildServiceStreamUrl(ref)
        }
    }

    fun buildServiceStreamUrl(ref: String): String {
        if (mProfile!!.getHost() == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        var serviceRef = ref
        if (serviceRef.contains("http")) {
            try {
                return URLDecoder.decode(
                    serviceRef.substring(serviceRef.indexOf("http")),
                    "utf-8",
                ).replace(" ", "%20")
            } catch (_: UnsupportedEncodingException) {
            }
        }
        try {
            serviceRef = URLEncoder.encode(serviceRef, "utf-8").replace("+", "%20")
        } catch (_: UnsupportedEncodingException) {
        }
        var streamLoginString = ""
        if (mProfile!!.isStreamLogin()) {
            streamLoginString = mProfile!!.getUser() + ":" + mProfile!!.getPass() + "@"
        }
        return "http://" + streamLoginString + mProfile!!.getStreamHost() + ":" +
            mProfile!!.getStreamPortString() + "/" + serviceRef
    }

    fun buildFileStreamUrl(ref: String, fileName: String?): String {
        if (mProfile!!.getHost() == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        if (mProfile!!.isEncoderStream() && ref.startsWith("1:")) {
            return buildEncoderStreamUrl(ref)
        }
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("file", fileName))
        val parms = NameValuePair.toString(params)
        var fileAuthString = ""
        if (mProfile!!.isFileLogin()) {
            fileAuthString = mProfile!!.getUser() + ":" + mProfile!!.getPass() + "@"
        }
        return mFilePrefix + fileAuthString + mProfile!!.getStreamHost() + ":" +
            mProfile!!.getFilePortString() + URIStore.FILE + parms
    }

    fun fetchPageContent(uri: String): Boolean = fetchPageContent(uri, ArrayList())

    private fun isSessionLess(uri: String): Boolean = URIStore.SCREENSHOT == uri

    private fun authHeader(): String? {
        if (!mProfile!!.isLogin()) return null
        return Credentials.basic(mProfile!!.getUser().orEmpty(), mProfile!!.getPass().orEmpty())
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
                val trustManager = DreamDroidTrustManager(appContext)
                val sc = SSLContext.getInstance("TLS")
                sc.init(null, arrayOf<X509TrustManager>(trustManager), SecureRandom())
                builder.sslSocketFactory(sc.socketFactory, trustManager)
                builder.hostnameVerifier(
                    trustManager.wrapHostnameVerifier(
                        HttpsURLConnection.getDefaultHostnameVerifier(),
                    ),
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "SSL setup for OkHttp failed", e)
            }
        }
        return builder.build()
    }

    fun fetchPageContent(uri: String, parameters: MutableList<NameValuePair>): Boolean {
        applyConfig()

        mErrorText = ""
        mErrorTextId = -1
        mError = false
        mBytes = ByteArray(0)
        var path = uri
        if (!path.startsWith("/")) {
            path = "/$path"
        }

        try {
            if (mProfile!!.getSessionId() != null && !isSessionLess(path)) {
                parameters.add(NameValuePair("sessionid", mProfile!!.getSessionId()))
            }
            val urlString = buildUrl(path, parameters)
            val requestBuilder = Request.Builder().url(urlString)
            authHeader()?.let { requestBuilder.header("Authorization", it) }
            if (DreamDroid.featurePostRequest()) {
                requestBuilder.post(ByteArray(0).toRequestBody(null))
            } else {
                requestBuilder.get()
            }

            newClient().newCall(requestBuilder.build()).execute().use { response ->
                return handleResponse(path, parameters, urlString, response)
            }
        } catch (e: MalformedURLException) {
            mError = true
            mErrorTextId = R.string.illegal_host
        } catch (e: UnknownHostException) {
            mError = true
            mErrorText = null
            mErrorTextId = R.string.host_not_found
        } catch (e: ProtocolException) {
            mError = true
            mErrorText = e.localizedMessage
        } catch (e: ConnectException) {
            mError = true
            mErrorTextId = R.string.host_unreach
        } catch (e: IOException) {
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
            e.printStackTrace()
            mError = true
            mErrorText = e.localizedMessage
        } finally {
            if (mError) {
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
        mBytes = response.body?.bytes() ?: ByteArray(0)
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
            mProfile!!.setSessionId(content)
        } else {
            mProfile!!.setSessionId(null)
        }
    }

    private fun dumpToFile(urlString: String) {
        val externalStorage = Environment.getExternalStorageDirectory()
        if (!externalStorage.canWrite()) return

        val parts = urlString.split("/")
        val fn = parts[parts.size - 1].split("\\?".toRegex()).toTypedArray()[0]
        Log.w("--------------", fn)

        val base = String.format("%s/dreamDroid/xml", externalStorage)
        val file = File(String.format("%s/%s", base, fn))
        try {
            File(base).mkdirs()
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
        mPrefix = if (mProfile!!.isSsl()) "https://" else "http://"
        mFilePrefix = if (mProfile!!.isFileSsl()) "https://" else "http://"
    }

    fun setConnectionTimeoutMillis(millis: Int) {
        mConnectionTimeoutMillis = millis
    }

    companion object {
        @JvmField
        val LOG_TAG: String = SimpleHttpClient::class.java.simpleName

        @JvmField
        val BIG_BUCK_BUNNY_URL: String =
            "https://dreamdroid.org/bunny/big_buck_bunny_720p_h264.mov"

        @JvmStatic
        fun getInstance(): SimpleHttpClient = SimpleHttpClient()

        @JvmStatic
        fun getInstance(p: Profile?): SimpleHttpClient = SimpleHttpClient(p)
    }
}
