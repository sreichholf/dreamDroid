/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

/**
 * @author sreichholf
 *
 */
public class Message extends SimpleResult {
	public static final String TEXT = "message";
	public static final String TYPE = "type";
	public static final String TIMEOUT = "timeout";	
	public static final String MESSAGE_TYPE_WARNING = "1";
	public static final String MESSAGE_TYPE_INFO = "2";
	public static final String MESSAGE_TYPE_ERROR = "3";
	
//	public static String send(SimpleHttpClient shc, ExtendedHashMap message) {
//		
//		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
//		params.add(new BasicNameValuePair("text", message.getString(TEXT)));
//		params.add(new BasicNameValuePair("type", message.getString(TYPE)));
//		params.add(new BasicNameValuePair("timeout", message.getString(TIMEOUT)));
//		
//		if (shc.fetchPageContent(URIStore.MESSAGE, params)) {
//			return shc.getPageContentString();
//		}
//
//		return null;
//	}
	
	public static ArrayList<NameValuePair> getParams(ExtendedHashMap message){
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("text", message.getString(TEXT)));
		params.add(new BasicNameValuePair("type", message.getString(TYPE)));
		params.add(new BasicNameValuePair("timeout", message.getString(TIMEOUT)));
		
		return params;
	}
}
