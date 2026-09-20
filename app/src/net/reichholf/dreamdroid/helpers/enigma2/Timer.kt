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
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * Timer XML field names, after-event enums, and request helpers. UI uses [TypedTimer].
 */
class Timer {
    enum class TimerStates(private val value: Int) {
        WAITING(0),
        PREPARED(1),
        RUNNING(2),
        ENDED(3);

        override fun toString(): String = value.toString()

        fun intValue(): Int = value

        fun getText(ac: Activity): String =
            ac.resources.getTextArray(R.array.afterevents)[value] as String
    }

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
        const val KEY_EIT: String = "eit"
        const val KEY_NAME: String = "name"
        const val KEY_DESCRIPTION: String = "description"
        const val KEY_DESCRIPTION_EXTENDED: String = "descriptionex"
        const val KEY_DISABLED: String = "disabled"
        const val KEY_BEGIN: String = "begin"
        const val KEY_BEGIN_READEABLE: String = "begin_readable"
        const val KEY_END: String = "end"
        const val KEY_END_READABLE: String = "end_readable"
        const val KEY_DURATION: String = "duration"
        const val KEY_DURATION_READABLE: String = "duration_readable"
        const val KEY_START_PREPARE: String = "startprepare"
        const val KEY_JUST_PLAY: String = "justplay"
        const val KEY_AFTER_EVENT: String = "afterevent"
        const val KEY_LOCATION: String = "location"
        const val KEY_TAGS: String = "tags"
        const val KEY_LOG_ENTRIES: String = "logentries"
        const val KEY_FILE_NAME: String = "filename"
        const val KEY_BACK_OFF: String = "backoff"
        const val KEY_NEXT_ACTIVATION: String = "nextactivation"
        const val KEY_FIRST_TRY_PREPARE: String = "firsttryprepare"
        const val KEY_STATE: String = "state"
        const val KEY_REPEATED: String = "repeated"
        const val KEY_DONT_SAVE: String = "dontsave"
        const val KEY_CANCELED: String = "canceled"
        const val KEY_TOGGLE_DISABLED: String = "toggledisabled"

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

        fun getSaveParams(timer: TypedTimer, timerOld: TypedTimer?): ArrayList<NameValuePair> {
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("sRef", timer.reference))
            params.add(NameValuePair("begin", timer.begin))
            params.add(NameValuePair("end", timer.end))
            params.add(NameValuePair("name", timer.name))
            params.add(NameValuePair("description", timer.description))
            params.add(NameValuePair("dirname", timer.location))
            params.add(NameValuePair("tags", timer.tags))
            params.add(NameValuePair("eit", timer.eit))
            params.add(NameValuePair("disabled", timer.disabled))
            params.add(NameValuePair("justplay", timer.justPlay))
            params.add(NameValuePair("afterevent", timer.afterEvent))
            params.add(NameValuePair("repeated", timer.repeated))
            if (timerOld != null) {
                params.add(NameValuePair("channelOld", timerOld.reference))
                params.add(NameValuePair("beginOld", timerOld.begin))
                params.add(NameValuePair("endOld", timerOld.end))
                params.add(NameValuePair("deleteOldOnSave", "1"))
            } else {
                params.add(NameValuePair("deleteOldOnSave", "0"))
            }
            return params
        }

        fun getEventIdParams(event: Event): ArrayList<NameValuePair> {
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("sRef", event.serviceReference))
            params.add(NameValuePair("eventid", event.eventId))
            return params
        }

        fun getDeleteParams(timer: TypedTimer): ArrayList<NameValuePair> {
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("sRef", timer.reference))
            params.add(NameValuePair("begin", timer.begin))
            params.add(NameValuePair("end", timer.end))
            return params
        }

        fun editUsingEvent(mph: MultiPaneHandler?, event: Event) {
            edit(mph, createByEvent(event), true)
        }

        fun edit(mph: MultiPaneHandler?, timer: TypedTimer, create: Boolean) {
            (mph as? MainActivity)?.phoneNav?.navigateToTimerEdit(timer, create)
        }
    }
}
