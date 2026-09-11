package net.reichholf.dreamdroid.ssl

import android.content.Context
import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class DreamDroidTrustManager(ctx: Context?) : HostnameVerifier, X509TrustManager {
    private var mDefaultTrustManager: X509TrustManager? = getDefaultTrustManager()
    private var mDefaultHostnameVerifier: HostnameVerifier? = null

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
        mDefaultHostnameVerifier = verifier
        return this
    }

    fun trustAllCertificates(): Boolean =
        DreamDroid.getCurrentProfile().isAllCertsTrusted

    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        if (trustAllCertificates()) return true
        return mDefaultHostnameVerifier!!.verify(hostname, session)
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (!trustAllCertificates()) {
            mDefaultTrustManager!!.checkClientTrusted(chain, authType)
        }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (!trustAllCertificates()) {
            mDefaultTrustManager!!.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    companion object {
        private val LOG_TAG: String = DreamDroidTrustManager::class.java.simpleName
    }
}
