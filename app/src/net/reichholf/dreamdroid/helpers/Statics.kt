package net.reichholf.dreamdroid.helpers

import net.reichholf.dreamdroid.R

object Statics {
    const val ACTION_SET_TIMER: Int = 0xc001
    const val ACTION_EDIT_TIMER: Int = 0xc002
    const val ACTION_IMDB: Int = 0xc003
    const val ACTION_FIND_SIMILAR: Int = 0xc004
    const val ACTION_EDIT: Int = 0xc012

    const val ITEM_NOW: Int = 0x6000
    const val ITEM_NEXT: Int = 0x6001
    const val ITEM_STREAM: Int = 0x6002
    const val ITEM_TOGGLE_STANDBY: Int = 0x6013
    const val ITEM_RESTART_GUI: Int = 0x6014
    const val ITEM_REBOOT: Int = 0x6015
    const val ITEM_SHUTDOWN: Int = 0x6016
    const val ITEM_SAVE: Int = R.id.menu_save
    const val ITEM_SET_DEFAULT: Int = R.id.menu_default
    const val ITEM_TAGS: Int = R.id.menu_tags
    const val ITEM_CLEANUP: Int = R.id.menu_cleanup
    const val ITEM_DETECT_DEVICES: Int = R.id.menu_detect_devices
    const val ITEM_DELETE: Int = R.id.menu_delete

    const val REQUEST_EDIT_TIMER: Int = 0x5000
    const val REQUEST_PICK_SERVICE: Int = 0x5001
    const val REQUEST_PICK_BOUQUET: Int = 0x5002
    const val REQUEST_EDIT_PROFILE: Int = 0x5003
}
