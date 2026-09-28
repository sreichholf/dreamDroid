/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.enigma.Movie as TypedMovie
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * XML field names and request helpers for movies. UI uses
 * [net.reichholf.dreamdroid.enigma.Movie].
 */
object Movie {
    const val KEY_REFERENCE: String = Service.KEY_REFERENCE
    const val KEY_DESCRIPTION: String = "description"
    const val KEY_DESCRIPTION_EXTENDED: String = "descriptionEx"
    const val KEY_SERVICE_NAME: String = Service.KEY_NAME
    const val KEY_TAGS: String = "tags"
    const val KEY_FILE_NAME: String = "filename"

    fun getDeleteParams(movie: TypedMovie): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("sRef", movie.reference))
        return params
    }
}
