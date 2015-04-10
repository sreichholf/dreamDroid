/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import java.util.ArrayList;

/**
 * @author sre
 *
 */
public class BundleHelper {
	public static ArrayList<String> toStringArrayList(CharSequence[] strings){
		ArrayList<String> list = new ArrayList<>();
		for (CharSequence string : strings) {
			list.add(string.toString());
		}
		return list;
	}
	
	public static CharSequence[] toCharSequenceArray(ArrayList<String> strings){
		CharSequence[] list = new CharSequence[strings.size()];
		for(int i = 0; i < strings.size(); ++i){
			list[i] = strings.get(i);
		}
		return list;
	}
}
