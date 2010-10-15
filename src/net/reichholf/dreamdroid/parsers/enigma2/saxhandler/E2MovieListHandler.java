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
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author sreichholf
 *
 */
public class E2MovieListHandler extends DefaultHandler {
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

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startDocument()
	 */
	@Override
	public void startDocument() {
		// TODO ???
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endDocument()
	 */
	@Override
	public void endDocument() throws SAXException {
		// TODO ???
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		
		if (localName.equals("e2movie")) {
			inMovie = true;
			mMovie = new ExtendedHashMap();
		} else if (localName.equals("e2servicereference")){
			inReference = true;
		} else if (localName.equals("e2title")) {
			inTitle = true;
		} else if (localName.equals("e2description")) {
			inDescription = true;
		} else if (localName.equals("e2descriptionextended")) {
			inDescriptionEx = true;
		} else if (localName.equals("e2servicename")) {
			inName = true;
		} else if (localName.equals("e2time")) {
			inTime = true;
		} else if (localName.equals("e2length")) {
			inLength = true;
		} else if (localName.equals("e2tags")) {
			inTags = true;
		} else if (localName.equals("e2filename")) {
			inFilename = true;
		} else if (localName.equals("e2filesize")) {
			inFilesize = true;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if (localName.equals("e2movie")) {
			inMovie = false;
			mMovielist.add(mMovie);
		} else if (localName.equals("e2servicereference")) {
			inReference = false;
		} else if (localName.equals("e2title")) {
			inTitle = false;
		} else if (localName.equals("e2description")) {
			inDescription = false;
		} else if (localName.equals("e2descriptionextended")) {
			inDescriptionEx = false;
		} else if (localName.equals("e2servicename")) {
			inName = false;
		} else if (localName.equals("e2time")) {
			inTime = false;
		} else if (localName.equals("e2length")) {
			inLength = false;
		} else if (localName.equals("e2tags")) {
			inTags = false;
		} else if (localName.equals("e2filename")) {
			inFilename = false;
		} else if (localName.equals("e2filesize")) {
			inFilesize = false;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inMovie) {
			if (inReference) {
				mMovie.putOrConcat(Movie.REFERENCE, value);
			} else if (inTitle) {
				mMovie.putOrConcat(Movie.TITLE, value);
			} else if (inDescription) {
				mMovie.putOrConcat(Movie.DESCRIPTION, value);
			} else if (inDescriptionEx) {
				mMovie.putOrConcat(Movie.DESCRIPTION_EXTENDED, value);
			} else if (inName) {
				mMovie.putOrConcat(Movie.SERVICE_NAME, value.replaceAll("\\p{Cntrl}", "")); //remove illegal chars
			} else if (inTime) {
				mMovie.putOrConcat(Movie.TIME, value);
				mMovie.putOrConcat(Movie.TIME_READABLE, DateTime.getDateTimeString(value));
			} else if (inLength) {
				mMovie.putOrConcat(Movie.LENGTH, value);				
			} else if (inTags) {
				mMovie.putOrConcat(Movie.TAGS, value);
			} else if (inFilename) {
				mMovie.putOrConcat(Movie.FILE_NAME, value);
			} else if (inFilesize) {
				mMovie.putOrConcat(Movie.FILE_SIZE, value);
				Long size = new Long(value);
				size /= ( 1024 * 1024);
				String size_readable = size + " MB";
				mMovie.putOrConcat(Movie.FILE_SIZE_READABLE, size_readable);
			}
		}
	}
}
