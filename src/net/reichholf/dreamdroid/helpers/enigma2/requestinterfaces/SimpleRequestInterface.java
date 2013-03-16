/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requestinterfaces;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;

import org.apache.http.NameValuePair;

/**
 * @author sre
 *
 */
public interface SimpleRequestInterface {
	public String get(SimpleHttpClient shc);
	public String get(SimpleHttpClient shc, ArrayList<NameValuePair> params);
	public boolean parse(String xml, ExtendedHashMap result);
	public ExtendedHashMap getDefault();
}
