package net.reichholf.dreamdroid.activities.abs

import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.app.Activity.OVERRIDE_TRANSITION_OPEN
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import javax.net.ssl.HttpsURLConnection
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.LocalNetworkPermissionRequest
import net.reichholf.dreamdroid.helpers.enigma2.PiconImageLoader
import net.reichholf.dreamdroid.ui.dialogs.DialogActionListener

/**
 * Created by Stephan on 06.11.13.
 */
open class BaseActivity :
    AppCompatActivity(),
    DialogActionListener {
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
        if (requestLocalNetworkOnCreate()) {
            localNetworkPermissionRequest.ensure(this)
        }
        applyConfiguredActivityTransitions()
    }

    /** Setup waits until the search step so the welcome animation stays uncovered. */
    protected open fun requestLocalNetworkOnCreate(): Boolean = true

    protected fun ensureLocalNetworkPermission() {
        localNetworkPermissionRequest.ensure(this)
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
        if (Build.VERSION.SDK_INT < 34 && activityAnimationsEnabled()) {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.activity_open_scale,
                R.anim.activity_close_translate
            )
        }
    }

    private fun activityAnimationsEnabled(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true)

    private fun applyConfiguredActivityTransitions() {
        if (!activityAnimationsEnabled()) {
            return
        }
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.activity_open_translate,
                R.anim.activity_close_scale
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.activity_open_scale,
                R.anim.activity_close_translate
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.activity_open_translate,
                R.anim.activity_close_scale
            )
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
    }

    companion object {
        private val TAG: String = BaseActivity::class.java.simpleName
    }
}
