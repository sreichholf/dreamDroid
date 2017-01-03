/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author sre
 */
public class ExtendedHashMapHelper {

	public static ExtendedHashMap restoreFromBundle(Bundle bundle, String key) {
		return (ExtendedHashMap) bundle.getParcelable(key);
	}

	public static ArrayList<ExtendedHashMap> restoreListFromBundle(Bundle bundle, String key) {
		ArrayList<ExtendedHashMap> l = new ArrayList<>();

		@SuppressWarnings("unchecked")
		ArrayList<HashMap<String, Object>> list = (ArrayList<HashMap<String, Object>>) bundle
				.getSerializable(key);

		for (HashMap<String, Object> aList : list) {
			l.add(new ExtendedHashMap(aList));
		}

		return l;
	}
}
