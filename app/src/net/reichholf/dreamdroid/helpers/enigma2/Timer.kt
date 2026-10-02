/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import android.app.Activity
import java.util.Date
import java.util.GregorianCalendar
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.helpers.DateTime

/**
 * Timer XML field names, after-event enums, and new-timer defaults. UI uses [TypedTimer].
 */
class Timer {

    enum class Afterevents(private val value: Int) {
        NOTHING(0),
        STANDBY(1),
        DEEP_STANDBY(2),
        AUTO(3);

        override fun toString(): String = value.toString()

        fun intValue(): Int = value

        fun getText(ac: Activity): String =
            ac.resources.getTextArray(R.array.afterevents)[value] as String
    }

    companion object {
        const val DATA: String = "data"

        const val KEY_REFERENCE: String = "reference"
        const val KEY_SERVICE_NAME: String = "servicename"
        const val KEY_NAME: String = "name"
        const val KEY_DESCRIPTION: String = "description"
        const val KEY_DESCRIPTION_EXTENDED: String = "descriptionex"
        const val KEY_TAGS: String = "tags"
        const val KEY_FILE_NAME: String = "filename"
        const val KEY_STATE: String = "state"

        fun getInitialTimer(): TypedTimer {
            val cal = GregorianCalendar.getInstance()
            cal.time = Date()
            val s = cal.timeInMillis / 1000
            val e = s + 3600
            return TypedTimer(
                description = "",
                location = "/hdd/movie/",
                disabled = "0",
                justPlay = "0",
                afterEvent = Afterevents.AUTO.toString(),
                repeated = "0",
                begin = s.toString(),
                end = e.toString()
            )
        }

        fun createByEvent(event: Event): TypedTimer {
            val duration = DateTime.parseTimestamp(event.duration)
            val end = duration + DateTime.parseTimestamp(event.start)
            return getInitialTimer().copy(
                begin = event.start,
                end = end.toString(),
                name = event.title,
                description = event.description,
                descriptionExtended = event.descriptionExtended,
                serviceName = event.serviceName,
                reference = event.serviceReference
            )
        }
    }
}
