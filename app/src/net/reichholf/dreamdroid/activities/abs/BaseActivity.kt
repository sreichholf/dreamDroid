package net.reichholf.dreamdroid.activities.abs

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.helpers.PiconSyncService
import net.reichholf.dreamdroid.ssl.DreamDroidTrustManager
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
 * Created by Stephan on 06.11.13.
 */
open class BaseActivity :
    AppCompatActivity(),
    ActionDialog.DialogActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
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
        try {
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
        super.onCreate(savedInstanceState)
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS,
                true,
            )
        ) {
            overridePendingTransition(R.animator.activity_open_translate, R.animator.activity_close_scale)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult($requestCode,$resultCode,$data")
        super.onActivityResult(requestCode, resultCode, data)
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment == null) continue
            fragment.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPause() {
        super.onPause()
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS,
                true,
            )
        ) {
            overridePendingTransition(R.animator.activity_open_scale, R.animator.activity_close_translate)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON ->
                if (granted) {
                    callPiconSyncIntent()
                }
            else -> {
                val details = supportFragmentManager.findFragmentById(R.id.detail_view)
                details?.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun startPiconSync() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            callPiconSyncIntent()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON,
            )
        }
    }

    protected fun callPiconSyncIntent() {
        if (isSyncServiceRunning()) {
            Toast.makeText(this, R.string.picon_sync_running, Toast.LENGTH_LONG).show()
            return
        }
        val piconSyncIntent = Intent(this, PiconSyncService::class.java)
        startService(piconSyncIntent)
        Toast.makeText(this, R.string.picon_sync_started, Toast.LENGTH_LONG).show()
    }

    private fun isSyncServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (PiconSyncService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    }

    fun getContext(): Context = this

    companion object {
        const val REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_PICON: Int = 0
        const val REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_SCREENSHOT: Int = 1
        const val REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE_BACKUP: Int = 3

        private val TAG: String = BaseActivity::class.java.simpleName

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
