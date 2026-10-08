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

/**
 * A service's picon. The picon image loader resolves it against the current profile:
 * [Picon.piconUri].
 */
data class PiconKey(val reference: String?, val name: String?)

/**
 * @author sre
 */
object Picon {

    /** Where synced picons are stored; one shared directory for all profiles. */
    fun localDir(context: Context): String =
        // App-specific storage: WRITE_EXTERNAL_STORAGE is a no-op when targeting 30+.
        String.format(
            "%s%spicons%s",
            context.filesDir.absolutePath,
            File.separator,
            File.separator
        )

    /** The receiver's picon directory for online picons; [path] is the profile's. */
    fun onlineBasepath(path: String): String = String.format("%s/", path)

    fun getPiconFileName(
        context: Context,
        base: String,
        useName: Boolean,
        reference: String?,
        name: String?
    ): String? {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_FAKE_PICON, false)
        ) {
            return String.format("%spicon_default.png", base)
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
        fileName = String.format("%s%s.png", base, fileName)
        return fileName
    }

    /** Whether picons are shown at all (global). */
    fun enabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)

    /**
     * Where [key]'s picon loads from: the receiver's `/file` URL when [profile] uses online
     * picons (with its path and naming), else a `file://` URI of the shared synced picon
     * (named by the global `use_name_as_picon_filename`). Null when no file name can be
     * built.
     */
    fun piconUri(context: Context, profile: Profile?, key: PiconKey): String? {
        if (profile?.piconsOnline == true) {
            val fileName = getPiconFileName(
                context,
                onlineBasepath(profile.piconsOnlinePath),
                profile.piconsOnlineUseName,
                key.reference,
                key.name
            ) ?: return null
            return onlinePiconUrl(profile, fileName)
        }
        val useName = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false)
        val fileName =
            getPiconFileName(context, localDir(context), useName, key.reference, key.name)
                ?: return null
        return String.format("file://%s", fileName)
    }

    /**
     * `/file?file=<picon dir>/<name>.png` on the profile's web port. The same URL serves
     * OpenWebif: its `/file` returns any existing path (OpenWebif
     * `plugin/controllers/file.py:50-83`), so the user's picon path keeps working on both
     * web interfaces. OpenWebif's own `/picon/` is mounted only when the box finds a picon
     * folder (`root.py:80-81,98-99`), and `getservices?picon=1` would carry a URL per
     * service through the service model; neither is used.
     */
    fun onlinePiconUrl(profile: Profile, fileName: String?): String = EnigmaUrls.page(
        profile,
        URIStore.FILE,
        listOf(NameValuePair("file", fileName))
    )

    fun clearCache(context: Context) {
        PiconImageLoader.clearCache(context)
    }
}
