package net.reichholf.dreamdroid.appwidget

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.ArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler

/**
 * Home-screen Virtual Remote click handler. Runs RCU HTTP on [Dispatchers.IO]
 * (replaces the old JobIntentService WidgetService path).
 */
object WidgetRemoteRequest {
    private const val TAG = "WidgetRemoteRequest"

    /** Stable action string (kept for existing PendingIntents). */
    const val ACTION_RCU = "net.reichholf.dreamdroid.appwidget.WidgetService.ACTION_RCU"

    const val KEY_KEYID = "key_id"
    const val KEY_WIDGETID = "widget_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun enqueue(context: Context, intent: Intent, onComplete: Runnable? = null) {
        val app = context.applicationContext
        scope.launch {
            try {
                doRemoteRequest(app, intent)
            } finally {
                onComplete?.run()
            }
        }
    }

    private fun doRemoteRequest(context: Context, intent: Intent) {
        val profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(
            context,
            intent.getIntExtra(KEY_WIDGETID, -1)
        ) ?: return

        val shc = SimpleHttpClient.getInstance(profile)
        val handler = RemoteCommandRequestHandler()
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("command", intent.getStringExtra(KEY_KEYID)))
        params.add(NameValuePair("rcu", "advanced"))
        val xml = handler.get(shc, params)

        if (xml != null) {
            val result = handler.parseSimpleResult(xml)
            if (Python.FALSE == result.state) {
                val stateText = result.stateText
                val errorText = stateText ?: context.getString(R.string.connection_error)
                Log.w(TAG, stateText.orEmpty())
                showToast(context, errorText)
            }
        } else if (shc.hasError()) {
            val errorText = shc.getErrorText(context).orEmpty()
            Log.w(TAG, errorText)
            showToast(context, errorText)
        }
    }

    private fun showToast(context: Context, text: String?) {
        if (text == null) return
        mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
