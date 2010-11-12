/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

/**
 * @author sreichholf
 * 
 */
public class Movie{
	public static final String REFERENCE = "reference";
	public static final String TITLE = "title";
	public static final String DESCRIPTION = "description";
	public static final String DESCRIPTION_EXTENDED = "descriptionEx";
	public static final String SERVICE_NAME = "servicename";
	public static final String TIME = "time";
	public static final String TIME_READABLE = "time_readable";
	public static final String LENGTH = "length";
	public static final String TAGS = "tags";
	public static final String FILE_NAME = "filename";
	public static final String FILE_SIZE = "filesize";
	public static final String FILE_SIZE_READABLE = "filesize_readable";

	public static ArrayList<NameValuePair> getDeleteParams(ExtendedHashMap movie){
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("sRef", movie.getString(Movie.REFERENCE)));
		
		return params;
	}
	
}
