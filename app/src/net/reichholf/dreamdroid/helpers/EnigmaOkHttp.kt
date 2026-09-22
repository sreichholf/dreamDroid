package net.reichholf.dreamdroid.helpers

import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Response

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

    /**
     * Coil picon client. Shares the pool/dispatcher of [client] for the same
     * timeout and trust-all. SSL is inherited from [base] (`sslSocketFactory`
     * with the same [DreamDroidTrustManager] instance). Auth is an interceptor
     * so webifs that never send 401 still receive [Authorization].
     */
    fun piconClient(timeoutMillis: Int, trustAll: Boolean): OkHttpClient {
        return client(timeoutMillis, trustAll).newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val header = piconAuthHeader(DreamDroid.currentProfileOrNull())
                if (header == null || request.header("Authorization") != null) {
                    chain.proceed(request)
                } else {
                    chain.proceed(
                        request.newBuilder().header("Authorization", header).build()
                    )
                }
            }
            .authenticator { _, response ->
                if (responseCount(response) >= 3) {
                    null
                } else {
                    val username = response.request.url.username
                    val password = response.request.url.password
                    if (username.isEmpty() && password.isEmpty()) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header(
                                "Authorization",
                                Credentials.basic(username, password)
                            )
                            .build()
                    }
                }
            }
            .build()
    }

    fun piconAuthHeader(profile: Profile?): String? {
        if (profile == null || !profile.login) return null
        return Credentials.basic(profile.user.orEmpty(), profile.pass.orEmpty())
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
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
