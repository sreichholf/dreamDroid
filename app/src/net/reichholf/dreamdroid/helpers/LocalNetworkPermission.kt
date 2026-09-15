package net.reichholf.dreamdroid.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) blocks LAN sockets unless [ACCESS_LOCAL_NETWORK] is granted.
 * Enigma2 HTTP, mDNS autodiscovery, FTP picon sync, and streaming all need it.
 */
object LocalNetworkPermission {
    const val PERMISSION: String = Manifest.permission.ACCESS_LOCAL_NETWORK

    /** Platform enforcement starts at API 37; lower SDKs keep implicit INTERNET LAN access. */
    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean {
        if (!isRequired()) {
            return true
        }
        return ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Registers an Activity Result launcher (must be constructed before STARTED) and
 * prompts for [LocalNetworkPermission.PERMISSION] when targeting API 37+.
 */
class LocalNetworkPermissionRequest(
    activity: ComponentActivity,
    private val onGranted: () -> Unit = {}
) {
    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onGranted()
            }
        }

    fun ensure(activity: ComponentActivity) {
        if (!LocalNetworkPermission.isRequired()) {
            return
        }
        if (LocalNetworkPermission.isGranted(activity)) {
            return
        }
        launcher.launch(LocalNetworkPermission.PERMISSION)
    }
}
