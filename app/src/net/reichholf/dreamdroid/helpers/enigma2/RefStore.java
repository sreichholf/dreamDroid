/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */


package net.reichholf.dreamdroid.helpers.enigma2;

import androidx.annotation.NonNull;

/**
 * @author sreichholf
 *
 */
public class RefStore {
	@NonNull
	public static String TV_BOUQUETS = "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) FROM BOUQUET \"bouquets.tv\" ORDER BY bouquet";
	@NonNull
	public static String TV_PROVIDER = "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) FROM PROVIDERS ORDER BY name";
	@NonNull
	public static String TV_ALL = "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || (type == 25) ORDER BY name";

	@NonNull
	public static String RADIO_BOUQUETS = "1:7:2:0:0:0:0:0:0:0:(type == 2)FROM BOUQUET \"bouquets.radio\" ORDER BY bouquet";
	@NonNull
	public static String RADIO_PROVIDERS = "1:7:2:0:0:0:0:0:0:0:(type == 2) FROM PROVIDERS ORDER BY name";
	@NonNull
	public static String RADIO_ALL = "1:7:2:0:0:0:0:0:0:0:(type == 2) ORDER BY name";
}
