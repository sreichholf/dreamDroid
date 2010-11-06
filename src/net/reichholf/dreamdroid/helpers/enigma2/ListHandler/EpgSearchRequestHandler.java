/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.ListHandler;

import java.util.ArrayList;

import org.apache.http.NameValuePair;

import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.abs.ListRequestHandler;

/**
 * @author sre
 *
 */
public class EpgSearchRequestHandler extends EpgListRequestHandler implements ListRequestHandler{
	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public String getList(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.EPG_SEARCH, params[0])){
			return shc.getPageContentString();
		}

		return null;
	}

}
