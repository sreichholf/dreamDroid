/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Python

/**
 * @author sreichholf
 */
class Event : ExtendedHashMap {
    constructor(data: ExtendedHashMap) {
        mMap = data.getHashMap()
    }

    fun id(): String = getString(KEY_EVENT_ID, "0") ?: "0"

    fun name(): String = getString(KEY_EVENT_NAME, "") ?: ""

    fun start(): Int = getInt(KEY_EVENT_START)

    fun startReadable(): String = getString(KEY_EVENT_START_READABLE, "") ?: ""

    fun startTimeReadable(): String = getString(KEY_EVENT_START_TIME_READABLE, "") ?: ""

    fun duration(): Int = getInt(KEY_EVENT_DURATION)

    fun durationReadable(): String = getString(KEY_EVENT_DURATION_READABLE, "") ?: ""

    fun remaining(): Int = getInt(KEY_EVENT_REMAINING)

    fun remainingReadable(): String = getString(KEY_EVENT_REMAINING_READABLE, "") ?: ""

    fun currentTime(): Int = getInt(KEY_CURRENT_TIME)

    fun title(): String = getString(KEY_EVENT_TITLE, "") ?: ""

    fun description(): String = getString(KEY_EVENT_DESCRIPTION, "") ?: ""

    fun descriptionExtended(): String =
        (getString(KEY_EVENT_DESCRIPTION_EXTENDED, "") ?: "").replace("\\n", "\n")

    fun reference(): String? = getString(KEY_SERVICE_REFERENCE)

    fun serviceName(): String? = getString(KEY_SERVICE_NAME)

    companion object {
        const val PREFIX_NOW: String = "now_"
        const val PREFIX_NEXT: String = "next"

        const val KEY_EVENT_ID: String = "eventid"
        const val KEY_EVENT_NAME: String = "eventname"
        const val KEY_EVENT_START: String = "eventstart"
        const val KEY_EVENT_START_READABLE: String = "eventstart_readable"
        const val KEY_EVENT_START_TIME_READABLE: String = "eventstarttime_readable"
        const val KEY_EVENT_DURATION: String = "eventduration"
        const val KEY_EVENT_DURATION_READABLE: String = "eventduration_readable"
        const val KEY_EVENT_REMAINING: String = "eventremaining"
        const val KEY_EVENT_REMAINING_READABLE: String = "eventremaining_readable"
        const val KEY_CURRENT_TIME: String = "currenttime"
        const val KEY_EVENT_TITLE: String = "eventtitle"
        const val KEY_EVENT_DESCRIPTION: String = "eventdescription"
        const val KEY_EVENT_DESCRIPTION_EXTENDED: String = "eventdescriptionextended"
        @JvmField
        val KEY_SERVICE_REFERENCE: String = Service.KEY_REFERENCE
        @JvmField
        val KEY_SERVICE_NAME: String = Service.KEY_NAME

        @JvmStatic
        fun supplementReadables(event: ExtendedHashMap) {
            supplementReadables("", event)
        }

        @JvmStatic
        fun supplementReadables(prefix: String, event: ExtendedHashMap) {
            val eventstart = event.getString(prefix + KEY_EVENT_START)

            if (Python.NONE != eventstart && eventstart != null) {
                val start = DateTime.getDateTimeString(eventstart)
                val starttime = DateTime.getTimeString(eventstart)
                val duration = try {
                    DateTime.getDurationString(
                        event.getString(prefix + KEY_EVENT_DURATION),
                        eventstart,
                    )
                } catch (_: NumberFormatException) {
                    // deal with WebInterface 1.5 => EVENT_DURATION is already a string
                    event.getString(prefix + KEY_EVENT_DURATION)
                }

                event.put(prefix + KEY_EVENT_START_READABLE, start)
                event.put(prefix + KEY_EVENT_START_TIME_READABLE, starttime)
                event.put(prefix + KEY_EVENT_DURATION_READABLE, duration)
            }

            var eventtitle = event.getString(prefix + KEY_EVENT_TITLE)
            if (Python.NONE == eventtitle || eventtitle == null) {
                // deal with WebInterface 1.5 => try EVENT_NAME instead of EVENT_TITLE
                eventtitle = event.getString(prefix + KEY_EVENT_NAME)
                if (eventtitle != null) {
                    event.put(prefix + KEY_EVENT_TITLE, eventtitle)
                } else {
                    event.put(prefix + KEY_EVENT_TITLE, "N/A")
                }
            }
        }

        @JvmStatic
        fun fromNext(serviceNowNext: ExtendedHashMap): ExtendedHashMap {
            val event = ExtendedHashMap(serviceNowNext)
            val keys = event.keySet().toTypedArray()
            val converted = ArrayList<String>()
            for (k in keys) {
                var key = k as String
                if (key.startsWith(PREFIX_NEXT)) {
                    val value = event.getString(key)
                    event.remove(key)
                    key = key.replaceFirst(PREFIX_NEXT, "")
                    event.put(key, value)
                    converted.add(key)
                } else if (key != KEY_SERVICE_NAME && key != KEY_SERVICE_REFERENCE && !converted.contains(key)) {
                    event.remove(key)
                }
            }
            return event
        }
    }
}
