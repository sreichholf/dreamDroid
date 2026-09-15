/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

/**
 * XML / preference field names for EPG events. UI uses [net.reichholf.dreamdroid.enigma.Event].
 */
object Event {
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
    const val KEY_SERVICE_REFERENCE: String = Service.KEY_REFERENCE
    const val KEY_SERVICE_NAME: String = Service.KEY_NAME
}
