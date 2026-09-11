package net.reichholf.dreamdroid.tv.activities

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager
import net.reichholf.dreamdroid.tv.ui.TvComposeHubHost
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Response
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Arrays
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Created by Stephan on 16.10.2016.
 *
 * Kotlin port of the TV browse host activity (Compose hub via [TvComposeHubHost]).
 */
class MainActivity : FragmentActivity() {
    private var mTrustManager: DreamDroidTrustManager? = null

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 3.1c-iv-f: Compose hub is the TV browse host (Leanback browse removed).
        TvComposeHubHost.install(this)
        try {
            // register DreamDroidTrustManager for HTTPS
            mTrustManager = DreamDroidTrustManager(this)

            val sc = SSLContext.getInstance("TLS")
            sc.init(
                null,
                arrayOf<X509TrustManager>(mTrustManager!!),
                java.security.SecureRandom(),
            )

            // HttpsURLConnection
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(
                mTrustManager!!.wrapHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier()),
            )
            HttpsURLConnection.setFollowRedirects(false)

            // Picasso w/ OkHttpClient
            val clientBuilder = OkHttpClient.Builder()
            clientBuilder
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
                    mTrustManager!!.wrapHostnameVerifier(
                        HttpsURLConnection.getDefaultHostnameVerifier(),
                    ),
                )
            val builder = Picasso.Builder(applicationContext)
            builder.downloader(OkHttp3Downloader(clientBuilder.build()))
            try {
                Picasso.setSingletonInstance(builder.build())
            } catch (_: IllegalStateException) {
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private fun systemDefaultTrustManager(): X509TrustManager {
            try {
                val trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(null as KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
                    throw IllegalStateException(
                        "Unexpected default trust managers:" + Arrays.toString(trustManagers),
                    )
                }
                return trustManagers[0] as X509TrustManager
            } catch (e: GeneralSecurityException) {
                throw AssertionError() // The system has no TLS. Just give up.
            }
        }
    }
}
