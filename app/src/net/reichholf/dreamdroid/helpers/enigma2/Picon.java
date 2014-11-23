/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import java.io.File;

/**
 * @author sre
 */
public class Picon {
	private static DisplayImageOptions sDisplayOptions = new DisplayImageOptions.Builder().resetViewBeforeLoading(true).cacheInMemory(true).build();
	private static AnimateImageDisplayListener sAnimateImageDisplayListener = new AnimateImageDisplayListener();

	public static String getBasepath(Context context){
		String basePath = String.format("%s%sdreamDroid%spicons%s", Environment.getExternalStorageDirectory()
				.getAbsolutePath(), File.separator, File.separator, File.separator);
		File f = new File(basePath);
		if(!f.canWrite()) {
			basePath = String.format("%s%spicons%s", context.getFilesDir().getAbsolutePath(), File.separator, File.separator);
		}
		return basePath;

	}

	public static class AnimateImageDisplayListener extends SimpleImageLoadingListener {
		@Override
		public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
			if (!PreferenceManager.getDefaultSharedPreferences(view.getContext()).getBoolean(DreamDroid.PREFS_KEY_ENABLE_ANIMATIONS, true))
				return;
			if (loadedImage != null) {
				ImageView imageView = (ImageView) view;
				FadeInBitmapDisplayer.animate(imageView, 200);
			}
		}
	}

	public static String getPiconFileName(Context context, ExtendedHashMap service, boolean useName) {
		String root = getBasepath(context);
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

	public static void setPiconForView(Context context, ImageView piconView,
									   ExtendedHashMap service) {
		setPiconForView(context, piconView, service, sAnimateImageDisplayListener);
	}

	public static void setPiconForView(Context context, ImageView piconView,
									   ExtendedHashMap service, AnimateImageDisplayListener animateImageDisplayListener) {
		if (piconView == null) {
			return;
		}
		if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED,
				false)) {
			piconView.setVisibility(View.GONE);
			return;
		}
		boolean useName = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_PICONS_USE_NAME, false);
		String fileName = getPiconFileName(context, service, useName);
		if (fileName == null) {
			piconView.setVisibility(View.GONE);
			return;
		}
		if (piconView.getVisibility() != View.VISIBLE)
			piconView.setVisibility(View.VISIBLE);

		ImageLoader.getInstance().displayImage("file://" + fileName, piconView, sDisplayOptions, animateImageDisplayListener);
	}
}
