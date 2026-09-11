/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import java.io.Serializable

/**
 * @author sreichholf
 */
class Movie : ExtendedHashMap, Serializable {
    constructor() : super()

    constructor(data: ExtendedHashMap) {
        mMap = data.getHashMap()
    }

    fun reference(): String = getString(KEY_REFERENCE, "") ?: ""

    fun title(): String = getString(KEY_TITLE, "") ?: ""

    fun description(): String = getString(KEY_DESCRIPTION, "") ?: ""

    fun descriptionExtended(): String =
        (getString(KEY_DESCRIPTION_EXTENDED, "") ?: "").replace("\\n", "\n")

    fun serviceName(): String = getString(KEY_SERVICE_NAME, "") ?: ""

    fun time(): String = getString(KEY_TIME, "") ?: ""

    fun timeReadable(): String = getString(KEY_TIME_READABLE, "") ?: ""

    fun tags(): ArrayList<String> {
        val t = getString(KEY_TAGS, "") ?: ""
        if (t.isEmpty()) return ArrayList()
        return ArrayList(t.split(" "))
    }

    fun length(): String = getString(KEY_LENGTH, "00:00") ?: "00:00"

    fun fileName(): String? = getString(KEY_FILE_NAME)

    fun fileSize(): String? = getString(KEY_FILE_SIZE)

    fun fileSizeReadable(): String? = getString(KEY_FILE_SIZE_READABLE)

    companion object {
        @JvmField
        val KEY_REFERENCE: String = Service.KEY_REFERENCE
        const val KEY_TITLE: String = "title"
        const val KEY_DESCRIPTION: String = "description"
        const val KEY_DESCRIPTION_EXTENDED: String = "descriptionEx"
        @JvmField
        val KEY_SERVICE_NAME: String = Service.KEY_NAME
        const val KEY_TIME: String = "time"
        const val KEY_TIME_READABLE: String = "time_readable"
        const val KEY_LENGTH: String = "length"
        const val KEY_TAGS: String = "tags"
        const val KEY_FILE_NAME: String = "filename"
        const val KEY_FILE_SIZE: String = "filesize"
        const val KEY_FILE_SIZE_READABLE: String = "filesize_readable"

        @JvmStatic
        fun getDeleteParams(movie: ExtendedHashMap): ArrayList<NameValuePair> {
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("sRef", movie.getString(KEY_REFERENCE)))
            return params
        }
    }
}
