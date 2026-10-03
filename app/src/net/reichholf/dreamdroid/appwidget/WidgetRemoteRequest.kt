package net.reichholf.dreamdroid.appwidget

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Python

/**
 * Home-screen Virtual Remote click handler. Runs RCU HTTP in the application scope on
 * [Dispatchers.IO] (replaces the old JobIntentService WidgetService path).
 */
object WidgetRemoteRequest {
    private const val TAG = "WidgetRemoteRequest"

    /** Stable action string (kept for existing PendingIntents). */
    const val ACTION_RCU = "net.reichholf.dreamdroid.appwidget.WidgetService.ACTION_RCU"

    const val KEY_KEYID = "key_id"
    const val KEY_WIDGETID = "widget_id"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun enqueue(context: Context, intent: Intent, onComplete: Runnable? = null) {
        val app = context.applicationContext
        val deps = WidgetEntryPoint.get(app)
        deps.applicationScope().launch(Dispatchers.IO) {
            try {
                doRemoteRequest(app, deps, intent)
            } finally {
                onComplete?.run()
            }
        }
    }

    private suspend fun doRemoteRequest(context: Context, deps: WidgetEntryPoint, intent: Intent) {
        val profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(
            context,
            deps.profileRepository(),
            intent.getIntExtra(KEY_WIDGETID, -1)
        ) ?: return
        val keyCode = intent.getStringExtra(KEY_KEYID)?.toIntOrNull() ?: return

        val response = deps.receiverApiFactory().forProfile(profile)
            .remoteCommand(keyCode, simpleRemote = false, longPress = false)
        val error = response.error
        val errorText = when {
            response.value == null && error != null -> error.resolve(context).orEmpty()
            response.value == null -> context.getString(R.string.connection_error)
            Python.FALSE == response.value.state -> response.value.stateText
            else -> null
        }
        if (errorText != null) {
            Log.w(TAG, errorText)
            showToast(context, errorText)
        }
    }

    private fun showToast(context: Context, text: String?) {
        if (text == null) return
        mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
