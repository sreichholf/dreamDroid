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
public interface ListRequestInterface {
	public String getList(SimpleHttpClient shc, ArrayList<NameValuePair> params);
	public String getList(SimpleHttpClient shc);
	public boolean parseList(String xml, ArrayList<ExtendedHashMap> list);
}
