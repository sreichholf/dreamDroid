/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.enigma2.Mediaplayer;

import org.xml.sax.Attributes;

public class E2MediaplayerCurrentHandler extends E2SimpleHandler {

	protected static final String TAG_E2ARTIST = "e2artist";
	protected static final String TAG_E2TITLE = "e2title";
	protected static final String TAG_E2ALBUM = "e2album";
	protected static final String TAG_E2YEAR = "e2year";
	protected static final String TAG_E2GENRE = "e2genre";
	protected static final String TAG_E2COVERFILE = "e2coverfile";

	private boolean inArtist;
	private boolean inTitle;
	private boolean inAlbum;
	private boolean inYear;
	private boolean inGenre;
	private boolean inCoverfile;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		switch (localName) {
			case TAG_E2ARTIST:
				inArtist = true;
				break;
			case TAG_E2TITLE:
				inTitle = true;
				break;
			case TAG_E2ALBUM:
				inAlbum = true;
				break;
			case TAG_E2YEAR:
				inYear = true;
				break;
			case TAG_E2GENRE:
				inGenre = true;
				break;
			case TAG_E2COVERFILE:
				inCoverfile = true;
				break;
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
		switch (localName) {
			case TAG_E2ARTIST:
				inArtist = false;
				break;
			case TAG_E2TITLE:
				inTitle = false;
				break;
			case TAG_E2ALBUM:
				inAlbum = false;
				break;
			case TAG_E2YEAR:
				inYear = false;
				break;
			case TAG_E2GENRE:
				inGenre = false;
				break;
			case TAG_E2COVERFILE:
				inCoverfile = false;
				break;
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

		if (inArtist) {
			mResult.putOrConcat(Mediaplayer.KEY_ARTIST, value);
		} else if (inTitle) {
			mResult.putOrConcat(Mediaplayer.KEY_TITLE, value);
		} else if (inAlbum) {
			mResult.putOrConcat(Mediaplayer.KEY_ALBUM, value);
		} else if (inYear) {
			mResult.putOrConcat(Mediaplayer.KEY_YEAR, value);
		} else if (inGenre) {
			mResult.putOrConcat(Mediaplayer.KEY_GENRE, value);
		} else if (inCoverfile) {
			mResult.putOrConcat(Mediaplayer.KEY_COVERFILE, value);
		}
	}
}
