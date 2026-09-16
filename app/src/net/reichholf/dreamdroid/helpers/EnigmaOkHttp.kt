package net.reichholf.dreamdroid.helpers

import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager
import okhttp3.OkHttpClient

/**
 * Process-wide OkHttp clients for Enigma2. Derived clients share the connection
 * pool and dispatcher of the matching SSL base.
 */
internal object EnigmaOkHttp {
    private const val LOG_TAG = "EnigmaOkHttp"

    private val lock = Any()
    private val derived = HashMap<Key, OkHttpClient>()
    private val bases = HashMap<Boolean, OkHttpClient>()

    fun client(timeoutMillis: Int, trustAll: Boolean): OkHttpClient {
        val key = Key(timeoutMillis, trustAll)
        synchronized(lock) {
            derived[key]?.let { return it }
            val created = base(trustAll).newBuilder()
                .connectTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            derived[key] = created
            return created
        }
    }

    private fun base(trustAll: Boolean): OkHttpClient {
        bases[trustAll]?.let { return it }
        val builder = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
        val appContext = DreamDroid.getAppContext()
        if (appContext != null) {
            try {
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
        val created = builder.build()
        bases[trustAll] = created
        return created
    }

    private data class Key(val timeoutMillis: Int, val trustAll: Boolean)
}
