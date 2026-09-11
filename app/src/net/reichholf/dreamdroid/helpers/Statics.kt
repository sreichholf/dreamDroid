package net.reichholf.dreamdroid.helpers

import net.reichholf.dreamdroid.R

object Statics {
    const val ACTION_SET_TIMER: Int = 0xc001
    const val ACTION_EDIT_TIMER: Int = 0xc002
    const val ACTION_IMDB: Int = 0xc003
    const val ACTION_FIND_SIMILAR: Int = 0xc004
    const val ACTION_DELETE: Int = 0xc009
    const val ACTION_DELETE_CONFIRMED: Int = 0xc011
    const val ACTION_EDIT: Int = 0xc012
    const val ACTION_LEAVE_CONFIRMED: Int = 0xc015
    const val ACTION_LOCATION_RATIONALE_DONE: Int = 0xc23
    const val ACTION_NONE: Int = 0xcfff

    const val ITEM_NOW: Int = 0x6000
    const val ITEM_NEXT: Int = 0x6001
    const val ITEM_STREAM: Int = 0x6002
    const val ITEM_TIMER: Int = 0x6003
    const val ITEM_MOVIES: Int = 0x6004
    const val ITEM_SERVICES: Int = 0x6005
    const val ITEM_INFO: Int = 0x6006
    const val ITEM_MESSAGE: Int = 0x6007
    const val ITEM_REMOTE: Int = 0x6008
    const val ITEM_PREFERENCES: Int = R.id.menu_navigation_settings
    const val ITEM_CURRENT: Int = 0x6010
    const val ITEM_SCREENSHOT: Int = 0x6012
    const val ITEM_TOGGLE_STANDBY: Int = 0x6013
    const val ITEM_RESTART_GUI: Int = 0x6014
    const val ITEM_REBOOT: Int = 0x6015
    const val ITEM_SHUTDOWN: Int = 0x6016
    const val ITEM_POWERSTATE_DIALOG: Int = 0x6017
    const val ITEM_ABOUT: Int = R.id.menu_navigation_about
    const val ITEM_CHECK_CONN: Int = R.id.menu_check_connectivity
    const val ITEM_CHANGELOG: Int = R.id.menu_navigation_changelog
    const val ITEM_SLEEPTIMER: Int = 0x6020
    const val ITEM_PROFILES: Int = R.id.menu_navigation_profiles
    const val ITEM_ADD_PROFILE: Int = 0x6023
    const val ITEM_SAVE: Int = R.id.menu_save
    const val ITEM_CANCEL: Int = R.id.menu_cancel
    const val ITEM_PICK_SERVICE: Int = 0x6027
    const val ITEM_PICK_BEGIN_DATE: Int = 0x6028
    const val ITEM_PICK_END_DATE: Int = 0x6029
    const val ITEM_PICK_REPEATED: Int = 0x6030
    const val ITEM_PICK_TAGS: Int = 0x6031
    const val ITEM_SELECT_LOCATION: Int = 0x6033
    const val ITEM_SET_DEFAULT: Int = R.id.menu_default
    const val ITEM_TAGS: Int = R.id.menu_tags
    const val ITEM_NEW_TIMER: Int = 0x6032
    const val ITEM_CLEANUP: Int = R.id.menu_cleanup
    const val ITEM_TOGGLE_ENABLED: Int = R.id.menu_toggle_enabled
    const val ITEM_HOME: Int = 0x6039
    const val ITEM_DETECT_DEVICES: Int = R.id.menu_detect_devices
    const val ITEM_SIGNAL: Int = 0x6040
    const val ITEM_ZAP: Int = 0x6041
    const val ITEM_DELETE: Int = R.id.menu_delete
    const val ITEM_BOUQUETEPG: Int = 0x6042
    const val ITEM_PICK_BEGIN_TIME: Int = 0x6043
    const val ITEM_PICK_END_TIME: Int = 0x6044
    const val ITEM_EDIT: Int = R.id.menu_edit

    const val REQUEST_EDIT_TIMER: Int = 0x5000
    const val REQUEST_PICK_SERVICE: Int = 0x5001
    const val REQUEST_PICK_BOUQUET: Int = 0x5002
    const val REQUEST_EDIT_PROFILE: Int = 0x5003
    const val REQUEST_BACKUP_IMPORT: Int = 0x5004
    const val REQUEST_DONATE: Int = 0x1337

    const val RESULT_NONE: Int = -9999

    const val INTENT_ACTION_PICK_BOUQUET: String = "pick_bouquet"
    const val TAG_PICON: String = "picon"
}
