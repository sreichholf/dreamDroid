/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;

import java.util.ArrayList;

/**
 * @author sre
 *
 */
public interface SimpleRequestInterface {
	@Nullable
	String get(SimpleHttpClient shc);
	@Nullable
	String get(SimpleHttpClient shc, ArrayList<NameValuePair> params);
	boolean parse(String xml, ExtendedHashMap result);
	@NonNull
	ExtendedHashMap getDefault();
}
