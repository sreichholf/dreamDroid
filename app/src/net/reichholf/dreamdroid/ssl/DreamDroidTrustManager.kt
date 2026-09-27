package net.reichholf.dreamdroid.ssl

import android.content.Context
import android.util.Log
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.ProfileRepository

class DreamDroidTrustManager(ctx: Context?, private val trustAll: Boolean) :
    HostnameVerifier,
    X509TrustManager {
    constructor(ctx: Context?) : this(
        ctx,
        ProfileRepository.get().requireCurrent().allCertsTrusted
    )

    private var platformTrustManager: X509TrustManager? = getDefaultTrustManager()

    private var defaultHostnameVerifier: HostnameVerifier? = null

    fun getDefaultTrustManager(): X509TrustManager? {
        try {
            val tmf = TrustManagerFactory.getInstance("X509")
            tmf.init(null as KeyStore?)
            for (t in tmf.trustManagers) {
                if (t is X509TrustManager) {
                    return t
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "getDefaultTrustManager(): $e")
        }
        return null
    }

    fun wrapHostnameVerifier(verifier: HostnameVerifier): HostnameVerifier {
        defaultHostnameVerifier = verifier
        return this
    }

    fun trustAllCertificates(): Boolean = trustAll

    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        if (trustAllCertificates()) return true
        return defaultHostnameVerifier!!.verify(hostname, session)
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (!trustAllCertificates()) {
            platformTrustManager!!.checkClientTrusted(chain, authType)
        }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (!trustAllCertificates()) {
            platformTrustManager!!.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        if (trustAllCertificates()) {
            return arrayOf()
        }
        return platformTrustManager?.acceptedIssuers ?: emptyArray()
    }

    companion object {
        private val LOG_TAG: String = DreamDroidTrustManager::class.java.simpleName
    }
}
