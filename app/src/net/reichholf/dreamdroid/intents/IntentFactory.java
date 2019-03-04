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
import android.preference.PreferenceManager;
import android.util.Log;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.activities.VideoActivity;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.Event;

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
		return getStreamServiceIntent(context, ref, title, null, null);
	}

	private static Intent getVideoIntent(Context context, String uriString) {
		Intent intent;
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true))
			intent = new Intent(context, VideoActivity.class);
		else
			intent = new Intent();
		intent.setAction(Intent.ACTION_VIEW);
		Uri uri = Uri.parse(uriString);
		intent.setDataAndType(uri, "video/*");

		return intent;
	}

	public static Intent getStreamServiceIntent(Context context, String ref, String title, String bouquetRef, ExtendedHashMap serviceInfo) {
		String uriString = SimpleHttpClient.getInstance().buildStreamUrl(ref);
		Log.i(DreamDroid.LOG_TAG, "Service-Streaming URL set to '" + uriString + "'");

		Intent intent = getVideoIntent(context, uriString);
		intent.putExtra("title", title);
		intent.putExtra("serviceRef", ref);
		if(bouquetRef != null)
			intent.putExtra("bouquetRef", bouquetRef);
		if(serviceInfo != null && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true))
			intent.putExtra("serviceInfo", serviceInfo);
		return intent;
	}

	public static Intent getStreamFileIntent(Context context, String ref, String fileName, String title, ExtendedHashMap fileInfo) {
		String uriString = SimpleHttpClient.getInstance().buildFileStreamUrl(ref, fileName);
		Log.i(DreamDroid.LOG_TAG, "File-Streaming URL set to '" + uriString + "'");
		Intent intent = getVideoIntent(context, uriString);
		intent.putExtra("title", title);
		if(fileInfo != null && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true))
			intent.putExtra("serviceInfo", fileInfo);
		return intent;
	}
}
