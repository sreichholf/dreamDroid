/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import android.view.View
import android.widget.ImageView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import java.io.File

/**
 * @author sre
 */
object Picon {
    @JvmStatic
    fun getBasepath(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, DreamDroid.isTV(context))) {
            return String.format(
                "%s/",
                sp.getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, "/usr/share/enigma2/picon"),
            )
        }

        if (!Environment.getExternalStorageDirectory().canWrite()) {
            return String.format(
                "%s%spicons%s",
                context.filesDir.absolutePath,
                File.separator,
                File.separator,
            )
        }

        return String.format(
            "%s%sdreamDroid%spicons%s",
            Environment.getExternalStorageDirectory().absolutePath,
            File.separator,
            File.separator,
            File.separator,
        )
    }

    @JvmStatic
    fun getPiconFileName(context: Context, service: ExtendedHashMap, useName: Boolean): String? =
        getPiconFileName(
            context,
            service.getString(Event.KEY_SERVICE_REFERENCE),
            service.getString(Event.KEY_SERVICE_NAME),
            useName,
        )

    @JvmStatic
    fun getPiconFileName(
        context: Context,
        reference: String?,
        name: String?,
        useName: Boolean,
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

    @JvmStatic
    fun setPiconForView(
        context: Context,
        piconView: ImageView?,
        service: ExtendedHashMap,
        tag: String,
    ) {
        setPiconForView(context, piconView, service, tag, null)
    }

    @JvmStatic
    fun setPiconForView(
        context: Context,
        piconView: ImageView?,
        service: ExtendedHashMap,
        tag: String,
        callback: Callback?,
    ) {
        setPiconForView(
            context,
            piconView,
            service.getString(Event.KEY_SERVICE_REFERENCE),
            service.getString(Event.KEY_SERVICE_NAME),
            tag,
            callback,
        )
    }

    @JvmStatic
    fun setPiconForView(
        context: Context,
        piconView: ImageView?,
        reference: String?,
        name: String?,
        tag: String,
        callback: Callback?,
    ) {
        if (piconView == null) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))) {
            piconView.visibility = View.GONE
            return
        }
        val useName = sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false)
        val fileName = getPiconFileName(context, reference, name, useName)
        if (fileName == null) {
            piconView.visibility = View.GONE
            return
        }
        if (piconView.visibility != View.VISIBLE) {
            piconView.visibility = View.VISIBLE
        }

        val uri = getPiconUri(context, fileName)
        Picasso.get().load(uri).fit().centerInside().tag(tag)
            .error(R.drawable.dreamdroid_logo_simple).into(piconView, callback)
    }

    @JvmStatic
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

    @JvmStatic
    fun clearCache() {
    }
}
