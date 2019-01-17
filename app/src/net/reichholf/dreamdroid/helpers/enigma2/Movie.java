/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.Nullable;

/**
 * @author sreichholf
 * 
 */
public class Movie extends ExtendedHashMap implements Serializable {

	public static final String KEY_REFERENCE = Service.KEY_REFERENCE;
	public static final String KEY_TITLE = "title";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_DESCRIPTION_EXTENDED = "descriptionEx";
	public static final String KEY_SERVICE_NAME = Service.KEY_NAME;
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

	public Movie() {
		super();
	}

	public Movie(ExtendedHashMap data) {
		mMap = data.getHashMap();
	}

	public String reference() {
		return getString(KEY_REFERENCE, "");
	}

	public String title() {
		return getString(KEY_TITLE, "");
	}

	public String description() {
		return getString(KEY_DESCRIPTION, "");
	}

	public String descriptionExtended() {
		return getString(KEY_DESCRIPTION_EXTENDED, "").replace("\\n", "\n");
	}

	public String serviceName() {
		return getString(KEY_SERVICE_NAME, "");
	}

	public String time() {
		return getString(KEY_TIME, "");
	}

	public String timeReadable() {
		return getString(KEY_TIME_READABLE, "");
	}

	public ArrayList<String> tags() {
		String t = getString(KEY_TAGS, "");
		if (t.isEmpty())
			return new ArrayList<>();
		return new ArrayList<>(Arrays.asList(t.split(" ")));
	}

	public String length() {
		return getString(KEY_LENGTH, "00:00");
	}

	public String fileName() {
		return getString(KEY_FILE_NAME);
	}

	public String fileSize() {
		return getString(KEY_FILE_SIZE);
	}

	public String fileSizeReadable() {
		return getString(KEY_FILE_SIZE_READABLE);
	}
}
