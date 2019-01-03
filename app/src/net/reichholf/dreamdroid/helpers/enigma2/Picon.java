/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;

import java.io.File;
import java.util.ArrayList;

/**
 * @author sre
 */
public class Picon {

	public static String getBasepath(Context context){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		if(sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, DreamDroid.isTV(context))) {
			return String.format("%s/", sp.getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, "/usr/share/enigma2/picon"));
		}

		if(!Environment.getExternalStorageDirectory().canWrite())
			return String.format("%s%spicons%s", context.getFilesDir().getAbsolutePath(), File.separator, File.separator);

		return String.format("%s%sdreamDroid%spicons%s", Environment.getExternalStorageDirectory()
				.getAbsolutePath(), File.separator, File.separator, File.separator);
	}

	public static String getPiconFileName(Context context, ExtendedHashMap service, boolean useName) {
		String root = getBasepath(context);
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_FAKE_PICON, false))
			return  String.format("%spicon_default.png", root);

		String fileName;
		if(useName){
			fileName = service.getString(Event.KEY_SERVICE_NAME);
		} else {
			fileName = service.getString(Event.KEY_SERVICE_REFERENCE);
			if (fileName == null || !fileName.contains(":"))
				return fileName;

			fileName = fileName.substring(0, fileName.lastIndexOf(':'));
			fileName = fileName.replace(":", "_");

			if (fileName.endsWith("_"))
				fileName = fileName.substring(0, fileName.length() - 1);
		}
		fileName = String.format("%s%s.png", root, fileName);

		return fileName;
	}

	public static void setPiconForView(Context context, ImageView piconView, ExtendedHashMap service, String tag) {
		setPiconForView(context, piconView, service, tag, null);
	}

	public static void setPiconForView(Context context, ImageView piconView, ExtendedHashMap service, String tag, Callback callback) {
		if (piconView == null) {
			return;
		}
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		if (!sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED,
				DreamDroid.isTV(context))) {
			piconView.setVisibility(View.GONE);
			return;
		}
		boolean useName = sp.getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false);
		String fileName = getPiconFileName(context, service, useName);
		if (fileName == null) {
			piconView.setVisibility(View.GONE);
			return;
		}
		if (piconView.getVisibility() != View.VISIBLE)
			piconView.setVisibility(View.VISIBLE);

		String uri = getPiconUri(context, fileName);
		Picasso.get().load(uri).fit().centerInside().tag(tag).error(R.drawable.dreamdroid_logo_simple).into(piconView, callback);
	}

	public static String getPiconUri(Context context, String fileName) {
		//https://dm7080/file?file=%2F%2Fmedia%2Fhdd%2Fmovie%2F20160822%202245%20-%20BR%20Fernsehen%20S%C3%BCd%20HD%20-%20Irgendwie%20und%20Sowieso%20(12)%20-%20Miteinander%20-%20Auseinander%20-%2030-J%C3%84HRIGES%20JUBIL%C3%84UM.ts
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, DreamDroid.isTV(context))) {
			ArrayList<NameValuePair> params = new ArrayList<>();
			params.add(new NameValuePair("file", fileName));
			return SimpleHttpClient.getInstance().buildAuthedUrl(URIStore.FILE, params);
		}
		return String.format("file://%s", fileName);
	}

	public static void clearCache() {
	}
}
