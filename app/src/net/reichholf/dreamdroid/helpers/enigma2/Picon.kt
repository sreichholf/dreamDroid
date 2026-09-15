/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.preference.PreferenceManager
import coil3.load
import coil3.request.error
import coil3.size.Scale
import java.io.File
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * @author sre
 */
object Picon {
    interface Callback {
        fun onSuccess()

        fun onError(error: Exception?)
    }

    fun getBasepath(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, DreamDroid.isTV(context))) {
            return String.format(
                "%s/",
                sp.getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, "/usr/share/enigma2/picon")
            )
        }

        // App-specific storage: WRITE_EXTERNAL_STORAGE is a no-op when targeting 30+.
        return String.format(
            "%s%spicons%s",
            context.filesDir.absolutePath,
            File.separator,
            File.separator
        )
    }

    fun getPiconFileName(
        context: Context,
        reference: String?,
        name: String?,
        useName: Boolean
    ): String? {
        val root = getBasepath(context)
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_FAKE_PICON, false)
        ) {
            return String.format("%spicon_default.png", root)
        }

        var fileName: String?
        if (useName) {
            fileName = name
        } else {
            fileName = reference
            if (fileName == null || !fileName.contains(":")) return fileName

            fileName = fileName.substring(0, fileName.lastIndexOf(':'))
            fileName = fileName.replace(":", "_")

            if (fileName.endsWith("_")) {
                fileName = fileName.substring(0, fileName.length - 1)
            }
        }
        fileName = String.format("%s%s.png", root, fileName)
        return fileName
    }

    @Suppress("UNUSED_PARAMETER")
    fun setPiconForView(
        context: Context,
        piconView: ImageView?,
        reference: String?,
        name: String?,
        tag: String,
        callback: Callback?
    ) {
        if (piconView == null) return
        val uri = resolveLoadUri(context, reference, name)
        if (uri == null) {
            piconView.visibility = View.GONE
            return
        }
        if (piconView.visibility != View.VISIBLE) {
            piconView.visibility = View.VISIBLE
        }
        piconView.scaleType = ImageView.ScaleType.FIT_CENTER
        PiconImageLoader.install(context)
        piconView.load(uri) {
            scale(Scale.FIT)
            error(R.drawable.dreamdroid_logo_simple)
            listener(
                onSuccess = { _, _ -> callback?.onSuccess() },
                onError = { _, result ->
                    callback?.onError(result.throwable as? Exception)
                }
            )
        }
    }

    fun resolveLoadUri(context: Context, reference: String?, name: String?): String? {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))) {
            return null
        }
        val useName = sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false)
        val fileName = getPiconFileName(context, reference, name, useName) ?: return null
        return getPiconUri(context, fileName)
    }

    fun getPiconUri(context: Context, fileName: String?): String {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, DreamDroid.isTV(context))
        ) {
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("file", fileName))
            return SimpleHttpClient.getInstance().buildAuthedUrl(URIStore.FILE, params)
        }
        return String.format("file://%s", fileName)
    }

    fun clearCache(context: Context) {
        PiconImageLoader.clearCache(context)
    }
}
