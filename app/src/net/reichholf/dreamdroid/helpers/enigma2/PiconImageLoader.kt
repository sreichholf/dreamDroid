package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Arrays
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Process-wide Coil [ImageLoader] for Enigma2 picons.
 *
 * SSL/auth match the former Picasso OkHttp singleton: [DreamDroidTrustManager] on the
 * handshake plus hostname verifier, basic-auth authenticator from URL userinfo.
 * Do not mutate process-wide [HttpsURLConnection] SSL defaults; trust-all is per
 * OkHttp client.
 */
object PiconImageLoader {
    fun install(context: Context) {
        val appContext = context.applicationContext
        SingletonImageLoader.setSafe {
            try {
                newImageLoader(appContext)
            } catch (e: Exception) {
                e.printStackTrace()
                ImageLoader.Builder(appContext).build()
            }
        }
    }

    fun clearCache(context: Context) {
        install(context)
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    fun newImageLoader(context: Context): ImageLoader {
        val okHttpClient = newOkHttpClient(context)
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .build()
    }

    fun newOkHttpClient(context: Context): OkHttpClient {
        val trustManager = DreamDroidTrustManager(context.applicationContext)
        val sc = SSLContext.getInstance("TLS")
        sc.init(
            null,
            arrayOf<X509TrustManager>(trustManager),
            java.security.SecureRandom()
        )
        return OkHttpClient.Builder()
            .authenticator { _, response ->
                if (responseCount(response) >= 3) {
                    null
                } else {
                    val username = response.request.url.username
                    val password = response.request.url.password
                    val cred = Credentials.basic(username, password)
                    response.request.newBuilder().header("Authorization", cred).build()
                }
            }
            .sslSocketFactory(sc.socketFactory, systemDefaultTrustManager())
            // OkHttp 4: avoid okhttp3.internal.*; match HttpsURLConnection verifier wrap.
            .hostnameVerifier(
                trustManager.wrapHostnameVerifier(
                    HttpsURLConnection.getDefaultHostnameVerifier()
                )
            )
            .build()
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

    private fun systemDefaultTrustManager(): X509TrustManager {
        try {
            val trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers = trustManagerFactory.trustManagers
            if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
                throw IllegalStateException(
                    "Unexpected default trust managers:" + Arrays.toString(trustManagers)
                )
            }
            return trustManagers[0] as X509TrustManager
        } catch (_: GeneralSecurityException) {
            throw AssertionError() // The system has no TLS. Just give up.
        }
    }
}
