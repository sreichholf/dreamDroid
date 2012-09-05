/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.intents;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * @author sre
 *
 */
public class IntentFactory {
	/**
	 * @param event
	 */
	public static void queryIMDb(Context ctx, ExtendedHashMap event){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String uriString = "imdb:///find?q=" + event.getString(Event.KEY_EVENT_TITLE);
		intent.setData(Uri.parse(uriString));
		try{			
			ctx.startActivity(intent);
		} catch(ActivityNotFoundException anfex) {
			if(DreamDroid.getSharedPreferences().getBoolean("mobile_imdb", false)){
				uriString = "http://m.imdb.com/find?q=" + event.getString(Event.KEY_EVENT_TITLE);
			} else {
				uriString = "http://www.imdb.com/find?q=" + event.getString(Event.KEY_EVENT_TITLE);
			}
			intent.setData(Uri.parse(uriString));
			ctx.startActivity(intent);
		}
	}
	
	/**
	 * @param ref
	 *            A ServiceReference
	 */
	public static Intent getStreamServiceIntent(String ref) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String uriString = "http://" + DreamDroid.getActiveProfile().getStreamHost().trim() + ":" + DreamDroid.getActiveProfile().getStreamPortString() + "/" + ref;
		Log.i(DreamDroid.LOG_TAG, "Streaming URL set to '" + uriString + "'");

		intent.setDataAndType(Uri.parse(uriString), "video/*");
		
		return intent;
	}
	
	/**
	 * @param ref
	 *            A ServiceReference
	 */
	public static Intent getStreamFileIntent(String fileName) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		SimpleHttpClient shc = SimpleHttpClient.getInstance();
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("file", fileName));
		String uriString = shc.buildFileStreamUrl(URIStore.FILE, params);
		
		intent.setDataAndType(Uri.parse(uriString), "video/*");
		return intent;
	}
}
