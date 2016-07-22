/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import net.reichholf.dreamdroid.R;

/**
 * @author sre
 * 
 */
public class Statics {
	public static final int ACTION_SET_TIMER = 0xc001;
	public static final int ACTION_EDIT_TIMER = 0xc002;
	public static final int ACTION_IMDB = 0xc003;
	public static final int ACTION_FIND_SIMILAR = 0xc004;
	public static final int ACTION_DELETE = 0xc009;
	public static final int ACTION_DELETE_CONFIRMED = 0xc011;
	public static final int ACTION_EDIT = 0xc012;
	public static final int ACTION_LEAVE_CONFIRMED = 0xc015;
	public static final int ACTION_ADD_TO_PLAYLIST = 0xc17;
	public static final int ACTION_PLAY_MEDIA = 0xc18;
	public static final int ACTION_DELETE_FROM_PLAYLIST = 0xc19;
	public static final int ACTION_STATISTICS_AGREED = 0xc20;
	public static final int ACTION_STATISTICS_DENIED = 0xc21;
	public static final int ACTION_SHOW_PRIVACY_STATEMENT = 0xc22;
	public static final int ACTION_LOCATION_RATIONALE_DONE = 0xc23;
	public static final int ACTION_NONE = 0xcfff;

	public static final int ITEM_NOW = 0x6000;
	public static final int ITEM_NEXT = 0x6001;
	public static final int ITEM_STREAM = 0x6002;
	public static final int ITEM_TIMER = 0x6003;
	public static final int ITEM_MOVIES = 0x6004;
	public static final int ITEM_SERVICES = 0x6005;
	public static final int ITEM_INFO = 0x6006;
	public static final int ITEM_MESSAGE = 0x6007;
	public static final int ITEM_REMOTE = 0x6008;
	public static final int ITEM_PREFERENCES = R.id.menu_navigation_settings;
	public static final int ITEM_CURRENT = 0x6010;
	public static final int ITEM_SCREENSHOT = 0x6012;
	public static final int ITEM_TOGGLE_STANDBY = 0x6013;
	public static final int ITEM_RESTART_GUI = 0x6014;
	public static final int ITEM_REBOOT = 0x6015;
	public static final int ITEM_SHUTDOWN = 0x6016;
	public static final int ITEM_POWERSTATE_DIALOG = 0x6017;
	public static final int ITEM_ABOUT = R.id.menu_navigation_about;
	public static final int ITEM_CHECK_CONN = R.id.menu_check_connectivity;
    public static final int ITEM_CHANGELOG = R.id.menu_navigation_changelog;
	public static final int ITEM_SLEEPTIMER = 0x6020;
	public static final int ITEM_MEDIA_PLAYER = 0x6021;
	public static final int ITEM_PROFILES = R.id.menu_navigation_profiles;
	public static final int ITEM_ADD_PROFILE = 0x6023;
	public static final int ITEM_RELOAD = R.id.menu_reload;
	public static final int ITEM_SAVE = R.id.menu_save;
	public static final int ITEM_CANCEL = R.id.menu_cancel;
	public static final int ITEM_PICK_SERVICE = 0x6027;
	public static final int ITEM_PICK_BEGIN_DATE = 0x6028;
	public static final int ITEM_PICK_END_DATE = 0x6029;
	public static final int ITEM_PICK_REPEATED = 0x6030;
	public static final int ITEM_PICK_TAGS = 0x6031;
	public static final int ITEM_SELECT_LOCATION = 0x6033;
	public static final int ITEM_OVERVIEW = R.id.menu_overview;
	public static final int ITEM_SET_DEFAULT = R.id.menu_default;
    public static final int ITEM_SYNC_EPG = R.id.menu_sync_epg;
	public static final int ITEM_TAGS = R.id.menu_tags;
	public static final int ITEM_NEW_TIMER = 0x6032;
	public static final int ITEM_CLEANUP = R.id.menu_cleanup;
	public static final int ITEM_TOGGLE_ENABLED = R.id.menu_toggle_enabled;
	public static final int ITEM_HOME = 0x6039;
	public static final int ITEM_DETECT_DEVICES = R.id.menu_detect_devices;
	public static final int ITEM_SIGNAL = 0x6040;
	public static final int ITEM_ZAP = 0x6041;
	public static final int ITEM_DELETE = R.id.menu_delete;
	public static final int ITEM_BOUQUETEPG = 0x6042;
	public static final int ITEM_PICK_BEGIN_TIME = 0x6043;
	public static final int ITEM_PICK_END_TIME= 0x6044;
	public static final int ITEM_EDIT = R.id.menu_edit;


	public static final int REQUEST_EDIT_TIMER = 0x5000;
	public static final int REQUEST_PICK_SERVICE = 0x5001;
	public static final int REQUEST_PICK_BOUQUET = 0x5002;
	public static final int REQUEST_EDIT_PROFILE = 0x5003;
	public static final int REQUEST_DONATE = 0x1337;

	public static final int RESULT_NONE = -9999;

	public static final int ITEM_MEDIA_HOME = R.id.menu_home_media;
	public static final int ITEM_MEDIA_BACK = R.id.menu_navigation_back_media;
	public static final int ITEM_MEDIA_CLOSE = R.id.menu_close_media;



	public static final String INTENT_ACTION_PICK_BOUQUET = "pick_bouquet";
	public static final String TAG_PICON = "picon";

}
