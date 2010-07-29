/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2MovieListHandler;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

/**
 * @author sreichholf
 * 
 */
public class Movie extends AbstractRequestHandler {
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

	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public static String getList(SimpleHttpClient shc, ArrayList<NameValuePair>... params) {
		if (shc.fetchPageContent(URIStore.MOVIES, params[0])) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param xml
	 * @param list
	 * @return
	 */
	public static boolean parseList(String xml, ArrayList<ExtendedHashMap> list) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2MovieListHandler handler = new E2MovieListHandler(list);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}

	/**
	 * @param shc
	 * @param movie
	 * @return
	 */
	public static String delete(SimpleHttpClient shc, ExtendedHashMap movie) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		params.add(new BasicNameValuePair("sRef", movie.getString(Movie.REFERENCE)));

		if (shc.fetchPageContent(URIStore.MOVIE_DELETE, params)) {
			return shc.getPageContentString();
		}

		return null;
	}
}
