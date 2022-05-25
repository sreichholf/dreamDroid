/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces;

import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;

import java.util.ArrayList;

/**
 * @author sre
 *
 */
public interface ListRequestInterface {
	@Nullable
	String getList(SimpleHttpClient shc, ArrayList<NameValuePair> params);
	@Nullable
	String getList(SimpleHttpClient shc);
	boolean parseList(String xml, ArrayList<ExtendedHashMap> list);
}
