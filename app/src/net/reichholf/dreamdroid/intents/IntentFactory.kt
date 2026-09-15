package net.reichholf.dreamdroid.intents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.Serializable
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

object IntentFactory {
    fun queryIMDb(context: Context, event: Event) {
        val intent = Intent(Intent.ACTION_VIEW)
        var uriString = "imdb:///find?q=" + event.title
        intent.data = Uri.parse(uriString)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            uriString = if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("mobile_imdb", false)
            ) {
                "http://m.imdb.com/find?q=" + event.title
            } else {
                "http://www.imdb.com/find?q=" + event.title
            }
            intent.data = Uri.parse(uriString)
            context.startActivity(intent)
        }
    }

    fun getStreamServiceIntent(context: Context, ref: String, title: String): Intent =
        getStreamServiceIntent(context, ref, title, null, null)

    private fun getVideoIntent(context: Context, uriString: String): Intent {
        val intent = if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true)
        ) {
            Intent(context, VideoActivity::class.java)
        } else {
            Intent()
        }
        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(Uri.parse(uriString), "video/*")
        return intent
    }

    fun getStreamServiceIntent(
        context: Context,
        ref: String,
        title: String,
        bouquetRef: String?,
        serviceInfo: ServiceNowNext?
    ): Intent = streamIntent(
        context,
        SimpleHttpClient.getInstance().buildStreamUrl(ref),
        "Service-Streaming URL set to",
        title,
        ref,
        bouquetRef,
        serviceInfo
    )

    fun getStreamFileIntent(
        context: Context,
        ref: String,
        fileName: String?,
        title: String?,
        fileInfo: Movie?
    ): Intent {
        val uriString = SimpleHttpClient.getInstance().buildFileStreamUrl(ref, fileName)
        Log.i(DreamDroid.LOG_TAG, "File-Streaming URL set to '$uriString'")
        val intent = getVideoIntent(context, uriString)
        intent.putExtra("title", title)
        putServiceInfo(context, intent, fileInfo)
        return intent
    }

    private fun streamIntent(
        context: Context,
        uriString: String,
        logPrefix: String,
        title: String,
        ref: String,
        bouquetRef: String?,
        serviceInfo: Serializable?
    ): Intent {
        Log.i(DreamDroid.LOG_TAG, "$logPrefix '$uriString'")
        val intent = getVideoIntent(context, uriString)
        intent.putExtra("title", title)
        intent.putExtra("serviceRef", ref)
        if (bouquetRef != null) {
            intent.putExtra("bouquetRef", bouquetRef)
        }
        putServiceInfo(context, intent, serviceInfo)
        return intent
    }

    private fun putServiceInfo(context: Context, intent: Intent, serviceInfo: Serializable?) {
        if (serviceInfo != null &&
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true)
        ) {
            intent.putExtra("serviceInfo", serviceInfo)
        }
    }
}
