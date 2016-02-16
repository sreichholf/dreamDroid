/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.intents;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.activities.VideoActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;

import java.util.ArrayList;

/**
 * @author sre
 */
public class IntentFactory {
	/**
	 * @param event
	 */
	public static void queryIMDb(Context context, ExtendedHashMap event) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		String uriString = "imdb:///find?q=" + event.getString(Event.KEY_EVENT_TITLE);
		intent.setData(Uri.parse(uriString));
		try {
			context.startActivity(intent);
		} catch (ActivityNotFoundException anfex) {
			if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("mobile_imdb", false)) {
				uriString = "http://m.imdb.com/find?q=" + event.getString(Event.KEY_EVENT_TITLE);
			} else {
				uriString = "http://www.imdb.com/find?q=" + event.getString(Event.KEY_EVENT_TITLE);
			}
			intent.setData(Uri.parse(uriString));
			context.startActivity(intent);
		}
	}

	public static Intent getStreamServiceIntent(Context context, String ref, String title) {
		return getStreamServiceIntent(context, ref, title, null);
	}

	public static Intent getStreamServiceIntent(Context context, String ref, String title, ExtendedHashMap serviceInfo) {
		Intent intent = new Intent(context, VideoActivity.class);
		intent.setAction(Intent.ACTION_VIEW);
		String uriString = SimpleHttpClient.getInstance().buildStreamUrl(ref, title);
		Log.i(DreamDroid.LOG_TAG, "Service-Streaming URL set to '" + uriString + "'");

		Uri uri = Uri.parse(uriString);
		intent.setDataAndType(uri, "video/*");
		intent.putExtra("title", title);
		intent.putExtra("serviceInfo", (Parcelable) serviceInfo);
		return intent;
	}

	public static Intent getStreamFileIntent(String fileName, String title) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		SimpleHttpClient shc = SimpleHttpClient.getInstance();
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("file", fileName));
		Uri uri = Uri.parse(shc.buildFileStreamUrl(URIStore.FILE, params));
		Log.i(DreamDroid.LOG_TAG, "Streaming file: " + uri.getEncodedQuery());

		intent.setDataAndType(uri, "video/*");
		intent.putExtra("title", title);
		return intent;
	}
}
