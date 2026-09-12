package net.reichholf.dreamdroid.intents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.activities.VideoActivity
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.Event

object IntentFactory {
    @JvmStatic
    fun queryIMDb(context: Context, event: ExtendedHashMap) {
        val intent = Intent(Intent.ACTION_VIEW)
        var uriString = "imdb:///find?q=" + event.getString(Event.KEY_EVENT_TITLE)
        intent.data = Uri.parse(uriString)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            uriString = if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("mobile_imdb", false)
            ) {
                "http://m.imdb.com/find?q=" + event.getString(Event.KEY_EVENT_TITLE)
            } else {
                "http://www.imdb.com/find?q=" + event.getString(Event.KEY_EVENT_TITLE)
            }
            intent.data = Uri.parse(uriString)
            context.startActivity(intent)
        }
    }

    @JvmStatic
    fun getStreamServiceIntent(context: Context, ref: String, title: String): Intent {
        return getStreamServiceIntent(context, ref, title, null, null)
    }

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

    @JvmStatic
    fun getStreamServiceIntent(
        context: Context,
        ref: String,
        title: String,
        bouquetRef: String?,
        serviceInfo: ExtendedHashMap?,
    ): Intent {
        val uriString = SimpleHttpClient.getInstance().buildStreamUrl(ref)
        Log.i(DreamDroid.LOG_TAG, "Service-Streaming URL set to '$uriString'")
        val intent = getVideoIntent(context, uriString)
        intent.putExtra("title", title)
        intent.putExtra("serviceRef", ref)
        if (bouquetRef != null) {
            intent.putExtra("bouquetRef", bouquetRef)
        }
        if (serviceInfo != null &&
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true)
        ) {
            intent.putExtra("serviceInfo", serviceInfo)
        }
        return intent
    }

    @JvmStatic
    fun getStreamFileIntent(
        context: Context,
        ref: String,
        fileName: String?,
        title: String?,
        fileInfo: ExtendedHashMap?,
    ): Intent {
        val uriString = SimpleHttpClient.getInstance().buildFileStreamUrl(ref, fileName)
        Log.i(DreamDroid.LOG_TAG, "File-Streaming URL set to '$uriString'")
        val intent = getVideoIntent(context, uriString)
        intent.putExtra("title", title)
        if (fileInfo != null &&
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true)
        ) {
            intent.putExtra("serviceInfo", fileInfo)
        }
        return intent
    }
}
