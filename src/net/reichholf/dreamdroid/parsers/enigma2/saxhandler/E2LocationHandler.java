package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import java.util.ArrayList;

import net.reichholf.dreamdroid.helpers.enigma2.Location;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class E2LocationHandler  extends DefaultHandler{
	private boolean inLocation;
	private String mLocation;
	private ArrayList<String> mLocationlist; //List of locations

	/**
	 * @param list
	 */
	public E2LocationHandler(ArrayList<String> list) {
		mLocationlist = list;
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
		if (localName.equals(Location.LOCATION)) {
			inLocation = true;
			mLocation = "";
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if (localName.equals(Location.LOCATION)) {
			inLocation = false;
			mLocationlist.add(mLocation.trim());
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inLocation) {
			mLocation += value;
		}
	}
}
