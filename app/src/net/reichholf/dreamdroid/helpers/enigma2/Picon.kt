/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.File
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair

/** A picon on the receiver. The picon image loader turns it into the active profile's URL. */
data class OnlinePicon(val fileName: String)

/**
 * @author sre
 */
object Picon {

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

    /**
     * What the picon image loader loads for a service: a `file://` URI of a synced picon, or
     * an [OnlinePicon]. Null when picons are off.
     */
    fun resolveLoadModel(context: Context, reference: String?, name: String?): Any? {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))) {
            return null
        }
        val useName = sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false)
        val fileName = getPiconFileName(context, reference, name, useName) ?: return null
        return piconModel(context, fileName)
    }

    fun onlinePiconUrl(profile: Profile, fileName: String?): String = EnigmaUrls.page(
        profile,
        URIStore.FILE,
        listOf(NameValuePair("file", fileName))
    )

    private fun piconModel(context: Context, fileName: String): Any {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, DreamDroid.isTV(context))
        ) {
            return OnlinePicon(fileName)
        }
        return String.format("file://%s", fileName)
    }

    fun clearCache(context: Context) {
        PiconImageLoader.clearCache(context)
    }
}
