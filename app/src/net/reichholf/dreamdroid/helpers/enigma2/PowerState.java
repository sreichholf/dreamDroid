/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import androidx.annotation.NonNull;

import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.util.ArrayList;

/**
 * @author sre
 *
 */
public class PowerState {
	@NonNull
	public static String KEY_IN_STANDBY = "standby";
	
	@NonNull
	public static String CMD_SET = "set";
	
	@NonNull
	public static String STATE_GET = "-1";
	@NonNull
	public static String STATE_TOGGLE = "0";
	@NonNull
	public static String STATE_SHUTDOWN = "1";
	@NonNull
	public static String STATE_SYSTEM_REBOOT = "2";
	@NonNull
	public static String STATE_GUI_RESTART = "3";
	
	@NonNull
	public static ArrayList<NameValuePair> getStateParams(String state){
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("newstate", state) );
		
		return params;
	}
}
