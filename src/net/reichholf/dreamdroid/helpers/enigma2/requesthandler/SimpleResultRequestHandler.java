/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;

import org.apache.http.NameValuePair;

public abstract class SimpleResultRequestHandler{
	protected String mUri;
	
	public SimpleResultRequestHandler(String uri){
		mUri = uri;
	}
	
	public String get(SimpleHttpClient shc, ArrayList<NameValuePair> params){
		if (shc.fetchPageContent(mUri, params)) {
			return shc.getPageContentString();
		}

		return null;
	}
	
	public ExtendedHashMap parseSimpleResult(String xml){
		return SimpleResult.parseSimpleResult(xml);
	}
}
