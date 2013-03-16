/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

public class Tag {
	/**
	 * @param selectedTags
	 * @return
	 */
	public static String implodeTags(ArrayList<String> selectedTags){
		String tags = "";
		for (String tag : selectedTags) {
			if ("".equals(tags)) {
				tags = tags.concat(tag);
			} else {
				tags = tags.concat(" ").concat(tag);
			}
		}
		
		return tags;
	}
}
