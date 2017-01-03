/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.util.ArrayList;

/**
 * @author sreichholf
 * 
 */
public class Movie{
	public static final String KEY_REFERENCE = "reference";
	public static final String KEY_TITLE = "title";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_DESCRIPTION_EXTENDED = "descriptionEx";
	public static final String KEY_SERVICE_NAME = "servicename";
	public static final String KEY_TIME = "time";
	public static final String KEY_TIME_READABLE = "time_readable";
	public static final String KEY_LENGTH = "length";
	public static final String KEY_TAGS = "tags";
	public static final String KEY_FILE_NAME = "filename";
	public static final String KEY_FILE_SIZE = "filesize";
	public static final String KEY_FILE_SIZE_READABLE = "filesize_readable";

	public static ArrayList<NameValuePair> getDeleteParams(ExtendedHashMap movie){
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair("sRef", movie.getString(Movie.KEY_REFERENCE)));
		
		return params;
	}
	
}
