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
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext

object IntentFactory {
    fun queryIMDb(context: Context, event: Event) {
        val intent = Intent(Intent.ACTION_VIEW)
        var uriString = "imdb:///find?q=" + event.title
        intent.data = Uri.parse(uriString)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            uriString = if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(DreamDroid.PREFS_KEY_MOBILE_IMDB, false)
            ) {
                "http://m.imdb.com/find?q=" + event.title
            } else {
                "http://www.imdb.com/find?q=" + event.title
            }
            intent.data = Uri.parse(uriString)
            context.startActivity(intent)
        }
    }

    fun usesIntegratedPlayer(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true)

    /**
     * Playback intent for a stream URI. Honors [DreamDroid.PREFS_KEY_INTEGRATED_PLAYER]:
     * integrated player targets [VideoActivity]; otherwise a generic `ACTION_VIEW`.
     */
    fun videoPlaybackIntent(context: Context, uriString: String): Intent {
        val intent = if (usesIntegratedPlayer(context)) {
            Intent(context, VideoActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW)
        }
        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(Uri.parse(uriString), "video/*")
        return intent
    }

    /** Plays [stream], a live service of the list [bouquetRef]. */
    fun getStreamServiceIntent(
        context: Context,
        stream: LiveStream.Ready,
        title: String,
        bouquetRef: String? = null,
        serviceInfo: ServiceNowNext? = null
    ): Intent = streamIntent(
        context,
        stream.url,
        "Service-Streaming URL set to",
        title,
        stream.reference,
        bouquetRef,
        serviceInfo
    )

    /** Plays the recording at [uriString] (see `MovieRepository.streamUrl`). */
    fun getStreamFileIntent(
        context: Context,
        uriString: String,
        title: String?,
        fileInfo: Movie?
    ): Intent {
        Log.i(DreamDroid.LOG_TAG, "File-Streaming URL set to '$uriString'")
        val intent = videoPlaybackIntent(context, uriString)
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
        val intent = videoPlaybackIntent(context, uriString)
        intent.putExtra("title", title)
        intent.putExtra("serviceRef", ref)
        if (bouquetRef != null) {
            intent.putExtra("bouquetRef", bouquetRef)
        }
        putServiceInfo(context, intent, serviceInfo)
        return intent
    }

    private fun putServiceInfo(context: Context, intent: Intent, serviceInfo: Serializable?) {
        if (serviceInfo != null && usesIntegratedPlayer(context)) {
            intent.putExtra("serviceInfo", serviceInfo)
        }
    }
}
