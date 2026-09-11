/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.MainActivity
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

/**
 * @author sreichholf
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

        @JvmStatic
        fun getInitialTimer(): ExtendedHashMap {
            val timer = ExtendedHashMap()
            timer.put(KEY_DESCRIPTION, "")
            timer.put(KEY_LOCATION, "/hdd/movie/")
            timer.put(KEY_DISABLED, "0") // enabled
            timer.put(KEY_JUST_PLAY, "0") // record
            timer.put(KEY_AFTER_EVENT, Afterevents.AUTO.toString()) // auto
            timer.put(KEY_REPEATED, "0") // One-Time-Event

            val cal = GregorianCalendar.getInstance()
            cal.time = Date()

            val s = cal.timeInMillis / 1000
            val e = s + 3600

            timer.put(KEY_BEGIN, s.toString())
            timer.put(KEY_END, e.toString())

            return timer
        }

        @JvmStatic
        fun createByEvent(event: ExtendedHashMap): ExtendedHashMap {
            val timer = getInitialTimer()

            val start = event.getString(Event.KEY_EVENT_START)
            val duration = DateTime.parseTimestamp(event.getString(Event.KEY_EVENT_DURATION))
            val end = duration + DateTime.parseTimestamp(start)

            timer.put(KEY_BEGIN, start)
            timer.put(KEY_END, end.toString())
            timer.put(KEY_NAME, event.getString(Event.KEY_EVENT_TITLE))
            timer.put(KEY_DESCRIPTION, event.getString(Event.KEY_EVENT_DESCRIPTION))
            timer.put(KEY_DESCRIPTION_EXTENDED, event.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED))
            timer.put(KEY_SERVICE_NAME, event.getString(Event.KEY_SERVICE_NAME))
            timer.put(KEY_REFERENCE, event.getString(Event.KEY_SERVICE_REFERENCE))

            return timer
        }

        @JvmStatic
        fun getSaveParams(timer: ExtendedHashMap, timerOld: ExtendedHashMap?): ArrayList<NameValuePair> {
            val params = ArrayList<NameValuePair>()

            params.add(NameValuePair("sRef", timer.getString(KEY_REFERENCE)))
            params.add(NameValuePair("begin", timer.getString(KEY_BEGIN)))
            params.add(NameValuePair("end", timer.getString(KEY_END)))
            params.add(NameValuePair("name", timer.getString(KEY_NAME)))
            params.add(NameValuePair("description", timer.getString(KEY_DESCRIPTION)))
            params.add(NameValuePair("dirname", timer.getString(KEY_LOCATION)))
            params.add(NameValuePair("tags", timer.getString(KEY_TAGS)))
            params.add(NameValuePair("eit", timer.getString(KEY_EIT)))
            params.add(NameValuePair("disabled", timer.getString(KEY_DISABLED)))
            params.add(NameValuePair("justplay", timer.getString(KEY_JUST_PLAY)))
            params.add(NameValuePair("afterevent", timer.getString(KEY_AFTER_EVENT)))
            params.add(NameValuePair("repeated", timer.getString(KEY_REPEATED)))

            if (timerOld != null) {
                params.add(NameValuePair("channelOld", timerOld.getString(KEY_REFERENCE)))
                params.add(NameValuePair("beginOld", timerOld.getString(KEY_BEGIN)))
                params.add(NameValuePair("endOld", timerOld.getString(KEY_END)))
                params.add(NameValuePair("deleteOldOnSave", "1"))
            } else {
                params.add(NameValuePair("deleteOldOnSave", "0"))
            }

            return params
        }

        @JvmStatic
        fun getEventIdParams(event: ExtendedHashMap): ArrayList<NameValuePair> {
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("sRef", event.getString(Event.KEY_SERVICE_REFERENCE)))
            params.add(NameValuePair("eventid", event.getString(Event.KEY_EVENT_ID)))
            return params
        }

        @JvmStatic
        fun getDeleteParams(timer: ExtendedHashMap): ArrayList<NameValuePair> {
            val params = ArrayList<NameValuePair>()
            params.add(NameValuePair("sRef", timer.getString(KEY_REFERENCE)))
            params.add(NameValuePair("begin", timer.getString(KEY_BEGIN)))
            params.add(NameValuePair("end", timer.getString(KEY_END)))
            return params
        }

        @JvmStatic
        fun editUsingEvent(mph: MultiPaneHandler?, event: ExtendedHashMap, target: Fragment) {
            edit(mph, createByEvent(event), target, true)
        }

        @JvmStatic
        fun edit(mph: MultiPaneHandler?, timer: ExtendedHashMap?, target: Fragment, create: Boolean) {
            var walker = target.parentFragment
            while (walker != null) {
                if (walker is PhoneNavHostFragment && walker.navigateToTimerEdit(timer!!, create)) {
                    return
                }
                walker = walker.parentFragment
            }

            val activity = target.activity ?: return
            if (activity !is FragmentActivity) {
                return
            }
            val detail = activity.supportFragmentManager.findFragmentById(R.id.detail_view)
            if (detail is PhoneNavHostFragment && detail.navigateToTimerEdit(timer!!, create)) {
                return
            }
            val host = PhoneNavHostFragment.newInstance(PhoneNavRoutes.HUB)
            host.queueTimerEdit(timer!!, create)
            if (activity is MainActivity) {
                activity.showDetails(host)
            } else if (mph != null) {
                mph.showDetails(host)
            }
        }
    }
}
