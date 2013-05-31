/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.io.File;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.ImageLoader;
import android.content.Context;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;

/**
 * @author sre
 * 
 */
public class Picon {
	public static String getPiconFileName(ExtendedHashMap service) {
		String root = Environment.getExternalStorageDirectory().getAbsolutePath();
		String fileName = service.getString(Event.KEY_SERVICE_REFERENCE);
		if (fileName == null)
			return null;
		fileName = fileName.replace(":", "_");
		if (fileName.endsWith("_"))
			fileName = fileName.substring(0, fileName.length() - 1);

		fileName = String.format("%s%sdreamDroid%spicons%s%s.png", root, File.separator, File.separator,
				File.separator, fileName);

		return fileName;
	}

	public static void setPiconForView(Context context, ImageView piconView, ImageLoader imageLoader,
			ExtendedHashMap service) {
		if (piconView == null) {
			return;
		}
		if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("picons", false)) {
			piconView.setVisibility(View.GONE);
			return;
		}
		String fileName = getPiconFileName(service);
		if (fileName == null) {
			piconView.setVisibility(View.GONE);
			return;
		}
		if (piconView.getVisibility() != View.VISIBLE)
			piconView.setVisibility(View.VISIBLE);

		imageLoader.load(fileName, piconView);
	}
}
