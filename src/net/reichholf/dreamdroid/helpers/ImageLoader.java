//based on https://code.google.com/p/android-imageloader/
/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.reichholf.dreamdroid.helpers;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

/**
 * This helper class load images from the local file system and binds those with
 * the provided ImageView.
 * 
 * A local cache of loaded images is maintained internally to improve
 * performance.
 */
public class ImageLoader {
	private static final String LOG_TAG = "ImageLoader";

	public enum Mode {
		NO_ASYNC_TASK, NO_LOADED_DRAWABLE, CORRECT
	}

	private Mode mode = Mode.NO_ASYNC_TASK;

	/**
	 * Loads the specified image from the local file system and binds it to the
	 * provided ImageView. The binding is immediate if the image is found in the
	 * cache and will be done asynchronously otherwise. A null bitmap will be
	 * associated to the ImageView if an error occurs.
	 * 
	 * @param pathName
	 *            The path of the image to load.
	 * @param imageView
	 *            The ImageView to bind the loaded image to.
	 */
	public void load(String pathName, ImageView imageView) {
		resetPurgeTimer();
		Bitmap bitmap = getBitmapFromCache(pathName);

		if (bitmap == null) {
			forceLoad(pathName, imageView);
		} else {
			cancelPotentialLoad(pathName, imageView);
			imageView.setImageBitmap(bitmap);
		}
	}

	/*
	 * Same as load but the image is always loaded and the cache is not used.
	 * Kept private at the moment as its interest is not clear. private void
	 * forceLoad(String url, ImageView view)loadLoad(url, view, null); }
	 */

	/**
	 * Same as load but the image is always loaded and the cache is not used.
	 * Kept private at the moment as its interest is not clear.
	 */
	private void forceLoad(String pathName, ImageView imageView) {
		// State sanity: url is guaranteed to never be null in
		// loadedDrawable and cache keys.
		if (pathName == null) {
			imageView.setImageDrawable(null);
			return;
		}

		if (cancelPotentialLoad(pathName, imageView)) {
			switch (mode) {
			case NO_ASYNC_TASK:
				Bitmap bitmap = loadBitmap(pathName);
				addBitmapToCache(pathName, bitmap);
				imageView.setImageBitmap(bitmap);
				break;

			case NO_LOADED_DRAWABLE:
				BitmapLoaderTask task = new BitmapLoaderTask(imageView);
				task.execute(pathName);
				break;

			case CORRECT:
				task = new BitmapLoaderTask(imageView);
				LoadedDrawable loadedDrawable = new LoadedDrawable(task);
				imageView.setImageDrawable(loadedDrawable);
				task.execute(pathName);
				break;
			}
		}
	}

	/**
	 * Returns true if the current load has been canceled or if there was no
	 * load in progress on this image view. Returns false if the load in
	 * progress deals with the same url. The load is not stopped in that case.
	 */
	private static boolean cancelPotentialLoad(String pathName, ImageView imageView) {
		BitmapLoaderTask bitmapLoaderTask = getBitmapLoaderTask(imageView);

		if (bitmapLoaderTask != null) {
			String bitmapPathName = bitmapLoaderTask.pathName;
			if ((bitmapPathName == null) || (!bitmapPathName.equals(pathName))) {
				bitmapLoaderTask.cancel(true);
			} else {
				// The same URL is already being loaded.
				return false;
			}
		}
		return true;
	}

	/**
	 * @param imageView
	 *            Any imageView
	 * @return Retrieve the currently active load task (if any) associated with
	 *         this imageView. null if there is no such task.
	 */
	private static BitmapLoaderTask getBitmapLoaderTask(ImageView imageView) {
		if (imageView != null) {
			Drawable drawable = imageView.getDrawable();
			if (drawable instanceof LoadedDrawable) {
				LoadedDrawable loadedDrawable = (LoadedDrawable) drawable;
				return loadedDrawable.getBitmapLoaderTask();
			}
		}
		return null;
	}

	Bitmap loadBitmap(String pathName) {
		File f = new File(pathName);
		if (f.exists())
			return BitmapFactory.decodeFile(pathName);
		return null;
	}

	/*
	 * An InputStream that skips the exact number of bytes provided, unless it
	 * reaches EOF.
	 */
	static class FlushedInputStream extends FilterInputStream {
		public FlushedInputStream(InputStream inputStream) {
			super(inputStream);
		}

		@Override
		public long skip(long n) throws IOException {
			long totalBytesSkipped = 0L;
			while (totalBytesSkipped < n) {
				long bytesSkipped = in.skip(n - totalBytesSkipped);
				if (bytesSkipped == 0L) {
					int b = read();
					if (b < 0) {
						break; // we reached EOF
					} else {
						bytesSkipped = 1; // we read one byte
					}
				}
				totalBytesSkipped += bytesSkipped;
			}
			return totalBytesSkipped;
		}
	}

	/**
	 * The actual AsyncTask that will asynchronously load the image.
	 */
	class BitmapLoaderTask extends AsyncTask<String, Void, Bitmap> {
		private String pathName;
		private final WeakReference<ImageView> imageViewReference;

		public BitmapLoaderTask(ImageView imageView) {
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		/**
		 * Actual load method.
		 */
		@Override
		protected Bitmap doInBackground(String... params) {
			pathName = params[0];
			return loadBitmap(pathName);
		}

		/**
		 * Once the image is loaded, associates it to the imageView
		 */
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled()) {
				bitmap = null;
			}

			addBitmapToCache(pathName, bitmap);

			if (imageViewReference != null) {
				ImageView imageView = imageViewReference.get();
				BitmapLoaderTask bitmapLoaderTask = getBitmapLoaderTask(imageView);
				// Change bitmap only if this process is still associated with
				// it
				// Or if we don't use any bitmap to task association
				// (NO_loaded_DRAWABLE mode)
				if ((this == bitmapLoaderTask) || (mode != Mode.CORRECT)) {
					imageView.setImageBitmap(bitmap);
				}
			}
		}
	}

	/**
	 * A fake Drawable that will be attached to the imageView while the load is
	 * in progress.
	 * 
	 * <p>
	 * Contains a reference to the actual load task, so that a load task can be
	 * stopped if a new binding is required, and makes sure that only the last
	 * started load process can bind its result, independently of the load
	 * finish order.
	 * </p>
	 */
	static class LoadedDrawable extends ColorDrawable {
		private final WeakReference<BitmapLoaderTask> bitmapLoaderTaskReference;

		public LoadedDrawable(BitmapLoaderTask bitmapLoaderTask) {
			super(Color.TRANSPARENT);
			bitmapLoaderTaskReference = new WeakReference<BitmapLoaderTask>(bitmapLoaderTask);
		}

		public BitmapLoaderTask getBitmapLoaderTask() {
			return bitmapLoaderTaskReference.get();
		}
	}

	public void setMode(Mode mode) {
		this.mode = mode;
		clearCache();
	}

	/*
	 * Cache-related fields and methods.
	 * 
	 * We use a hard and a soft cache. A soft reference cache is too
	 * aggressively cleared by the Garbage Collector.
	 */

	private static final int HARD_CACHE_CAPACITY = 10;
	private static final int DELAY_BEFORE_PURGE = 10 * 1000; // in milliseconds

	// Hard cache, with a fixed maximum capacity and a life duration
	private final HashMap<String, Bitmap> sHardBitmapCache = new LinkedHashMap<String, Bitmap>(HARD_CACHE_CAPACITY / 2,
			0.75f, true) {
		@Override
		protected boolean removeEldestEntry(LinkedHashMap.Entry<String, Bitmap> eldest) {
			if (size() > HARD_CACHE_CAPACITY) {
				// Entries push-out of hard reference cache are transferred to
				// soft reference cache
				sSoftBitmapCache.put(eldest.getKey(), new SoftReference<Bitmap>(eldest.getValue()));
				return true;
			} else
				return false;
		}
	};

	// Soft cache for bitmaps kicked out of hard cache
	private final static ConcurrentHashMap<String, SoftReference<Bitmap>> sSoftBitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>(
			HARD_CACHE_CAPACITY / 2);

	private final Handler purgeHandler = new Handler();

	private final Runnable purger = new Runnable() {
		public void run() {
			clearCache();
		}
	};

	/**
	 * Adds this bitmap to the cache.
	 * 
	 * @param bitmap
	 *            The newly loaded bitmap.
	 */
	private void addBitmapToCache(String pathName, Bitmap bitmap) {
		if (bitmap != null) {
			synchronized (sHardBitmapCache) {
				sHardBitmapCache.put(pathName, bitmap);
			}
		}
	}

	/**
	 * @param pathName
	 *            The URL of the image that will be retrieved from the cache.
	 * @return The cached bitmap or null if it was not found.
	 */
	private Bitmap getBitmapFromCache(String pathName) {
		// First try the hard reference cache
		synchronized (sHardBitmapCache) {
			final Bitmap bitmap = sHardBitmapCache.get(pathName);
			if (bitmap != null) {
				// Bitmap found in hard cache
				// Move element to first position, so that it is removed last
				sHardBitmapCache.remove(pathName);
				sHardBitmapCache.put(pathName, bitmap);
				return bitmap;
			}
		}

		// Then try the soft reference cache
		SoftReference<Bitmap> bitmapReference = sSoftBitmapCache.get(pathName);
		if (bitmapReference != null) {
			final Bitmap bitmap = bitmapReference.get();
			if (bitmap != null) {
				// Bitmap found in soft cache
				return bitmap;
			} else {
				// Soft reference has been Garbage Collected
				sSoftBitmapCache.remove(pathName);
			}
		}

		return null;
	}

	/**
	 * Clears the image cache used internally to improve performance. Note that
	 * for memory efficiency reasons, the cache will automatically be cleared
	 * after a certain inactivity delay.
	 */
	public void clearCache() {
		sHardBitmapCache.clear();
		sSoftBitmapCache.clear();
	}

	/**
	 * Allow a new delay before the automatic cache clear is done.
	 */
	private void resetPurgeTimer() {
		purgeHandler.removeCallbacks(purger);
		purgeHandler.postDelayed(purger, DELAY_BEFORE_PURGE);
	}
}
