package net.reichholf.dreamdroid.activities.abs

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import javax.net.ssl.HttpsURLConnection
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.PiconSyncService
import net.reichholf.dreamdroid.helpers.enigma2.PiconImageLoader
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener
import net.reichholf.dreamdroid.ui.nav.tintToolbarMenuIcons

/**
 * Created by Stephan on 06.11.13.
 */
open class BaseActivity :
    AppCompatActivity(),
    DialogActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val localNetworkPermissionRequest =
        LocalNetworkPermissionRequest(this) { onLocalNetworkPermissionGranted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            HttpsURLConnection.setFollowRedirects(false)
            // Coil ImageLoader w/ OkHttpClient. Do not mutate process-wide
            // HttpsURLConnection defaults; trust-all is per OkHttp client.
            PiconImageLoader.install(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        localNetworkPermissionRequest.ensure(this)
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS,
                true
            )
        ) {
            overridePendingTransition(
                R.animator.activity_open_translate,
                R.animator.activity_close_scale
            )
        }
    }

    /** Recheck the box after the user grants LAN access (API 37+). */
    protected open fun onLocalNetworkPermissionGranted() {
    }

    /**
     * Phone NavHost (Compose) may consume activity results.
     * [MainActivity] returns true after forwarding to [PhoneNavHandle].
     */
    open fun dispatchActivityResultToNavHandle(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult($requestCode,$resultCode,$data")
        super.onActivityResult(requestCode, resultCode, data)
        dispatchActivityResultToNavHandle(requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
                DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS,
                true
            )
        ) {
            overridePendingTransition(
                R.animator.activity_open_scale,
                R.animator.activity_close_translate
            )
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {
        // MenuHostHelper.onPrepareMenu runs after onPrepareOptionsMenu and can
        // replace icons (default bouquet fav/nofav). Tint after that.
        val shown = super.onPreparePanel(featureId, view, menu)
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            tintToolbarMenuIcons(findViewById<Toolbar>(R.id.toolbar), menu)
        }
        return shown
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
    }

    fun startPiconSync() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_PERMISSION_POST_NOTIFICATIONS_PICON
            )
        }
        callPiconSyncIntent()
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
        @Suppress("DEPRECATION")
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
        const val REQUEST_PERMISSION_POST_NOTIFICATIONS_PICON: Int = 0

        private val TAG: String = BaseActivity::class.java.simpleName
    }
}
