/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

/**
 * @author sreichholf
 */
public class Service extends ExtendedHashMap {
	public static final String KEY_NAME = "servicename";
	public static final String KEY_REFERENCE = "reference";

	public enum FLAGS {
		isDirectory(1),
		isMarker(64),
		isGroup(128),
		isLive(256);

		private int val;
		FLAGS(int v) {
			val = v;
		}

		public int value() {
			return val;
		}
	}

	public static int getFlags(@Nullable String ref) {
		int flags = 0;
		if (ref == null || ref.isEmpty())
			return flags;
		String f;
		try {
			f = ref.split(":")[1];
		} catch (ArrayIndexOutOfBoundsException ex) {
			return flags;
		}
		return Integer.parseInt(f);

	}

	public static boolean isDirectory(String ref) {
		return (getFlags(ref) & FLAGS.isDirectory.value()) == FLAGS.isDirectory.value();
	}

	public static boolean isBouquet(@NonNull String ref) {
		return ref.startsWith("1:7:");
	}

	public static boolean isMarker(String ref) {
		return (getFlags(ref) & FLAGS.isMarker.value()) == FLAGS.isMarker.value();
	}


}
