/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.DateTime;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author sreichholf
 * 
 */
public class E2MovieListHandler extends DefaultHandler {

	protected static final String TAG_E2MOVIE = "e2movie";
	protected static final String TAG_E2SERVICEREFERENCE = "e2servicereference";
	protected static final String TAG_E2TITLE = "e2title";
	protected static final String TAG_E2DESCRIPTION = "e2description";
	protected static final String TAG_E2DESCRIPTIONEXTENDED = "e2descriptionextended";
	protected static final String TAG_E2SERVICENAME = "e2servicename";
	protected static final String TAG_E2TIME = "e2time";
	protected static final String TAG_E2LENGTH = "e2length";
	protected static final String TAG_E2TAGS = "e2tags";
	protected static final String TAG_E2FILENAME = "e2filename";
	protected static final String TAG_E2FILESIZE = "e2filesize";

	private boolean inMovie = false;
	private boolean inReference = false;
	private boolean inTitle = false;
	private boolean inDescription = false;
	private boolean inDescriptionEx = false;
	private boolean inName = false;
	private boolean inTime = false;
	private boolean inLength = false;
	private boolean inTags = false;
	private boolean inFilename = false;
	private boolean inFilesize = false;

	private ExtendedHashMap mMovie;
	private ArrayList<ExtendedHashMap> mMovielist;

	/**
	 * @param list
	 */
	public E2MovieListHandler(ArrayList<ExtendedHashMap> list) {
		mMovielist = list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {

		if (localName.equals(TAG_E2MOVIE)) {
			inMovie = true;
			mMovie = new ExtendedHashMap();
		} else if (localName.equals(TAG_E2SERVICEREFERENCE)) {
			inReference = true;
		} else if (localName.equals(TAG_E2TITLE)) {
			inTitle = true;
		} else if (localName.equals(TAG_E2DESCRIPTION)) {
			inDescription = true;
		} else if (localName.equals(TAG_E2DESCRIPTIONEXTENDED)) {
			inDescriptionEx = true;
		} else if (localName.equals(TAG_E2SERVICENAME)) {
			inName = true;
		} else if (localName.equals(TAG_E2TIME)) {
			inTime = true;
		} else if (localName.equals(TAG_E2LENGTH)) {
			inLength = true;
		} else if (localName.equals(TAG_E2TAGS)) {
			inTags = true;
		} else if (localName.equals(TAG_E2FILENAME)) {
			inFilename = true;
		} else if (localName.equals(TAG_E2FILESIZE)) {
			inFilesize = true;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if (localName.equals(TAG_E2MOVIE)) {
			inMovie = false;
			mMovielist.add(mMovie);
		} else if (localName.equals(TAG_E2SERVICEREFERENCE)) {
			inReference = false;
		} else if (localName.equals(TAG_E2TITLE)) {
			inTitle = false;
		} else if (localName.equals(TAG_E2DESCRIPTION)) {
			inDescription = false;
		} else if (localName.equals(TAG_E2DESCRIPTIONEXTENDED)) {
			inDescriptionEx = false;
		} else if (localName.equals(TAG_E2SERVICENAME)) {
			inName = false;
		} else if (localName.equals(TAG_E2TIME)) {
			inTime = false;
		} else if (localName.equals(TAG_E2LENGTH)) {
			inLength = false;
		} else if (localName.equals(TAG_E2TAGS)) {
			inTags = false;
		} else if (localName.equals(TAG_E2FILENAME)) {
			inFilename = false;
		} else if (localName.equals(TAG_E2FILESIZE)) {
			inFilesize = false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inMovie) {
			if (inReference) {
				mMovie.putOrConcat(Movie.KEY_REFERENCE, value);
			} else if (inTitle) {
				mMovie.putOrConcat(Movie.KEY_TITLE, value);
			} else if (inDescription) {
				mMovie.putOrConcat(Movie.KEY_DESCRIPTION, value);
			} else if (inDescriptionEx) {
				mMovie.putOrConcat(Movie.KEY_DESCRIPTION_EXTENDED, value);
			} else if (inName) {
				mMovie.putOrConcat(Movie.KEY_SERVICE_NAME, value.replaceAll("\\p{Cntrl}", "")); // remove
																								// illegal
																								// chars
			} else if (inTime) {
				mMovie.putOrConcat(Movie.KEY_TIME, value);
				mMovie.putOrConcat(Movie.KEY_TIME_READABLE, DateTime.getDateTimeString(value));
			} else if (inLength) {
				mMovie.putOrConcat(Movie.KEY_LENGTH, value);
			} else if (inTags) {
				mMovie.putOrConcat(Movie.KEY_TAGS, value);
			} else if (inFilename) {
				mMovie.putOrConcat(Movie.KEY_FILE_NAME, value);
			} else if (inFilesize) {
				mMovie.putOrConcat(Movie.KEY_FILE_SIZE, value);
				Long size = new Long(value);
				size /= (1024 * 1024);
				String size_readable = size + " MB";
				mMovie.putOrConcat(Movie.KEY_FILE_SIZE_READABLE, size_readable);
			}
		}
	}
}
