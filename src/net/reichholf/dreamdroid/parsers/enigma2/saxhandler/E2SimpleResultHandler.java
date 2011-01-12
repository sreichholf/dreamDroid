package net.reichholf.dreamdroid.parsers.enigma2.saxhandler;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class E2SimpleResultHandler extends DefaultHandler{
	private boolean inState;
	private boolean inStateText;

	private ExtendedHashMap mResult;

	/**
	 * @param list
	 */
	public E2SimpleResultHandler(ExtendedHashMap res) {
		mResult = res;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String namespaceUri, String localName, String qName, Attributes attrs) {
		if (localName.equals("e2state") || localName.equals("e2result")) {
			inState = true;
		} else if (localName.equals("e2statetext") || localName.equals("e2resulttext")) {
			inStateText = true;
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String namespaceURI, String localName, String qName) {
		if (localName.equals("e2state") || localName.equals("e2result")) {
			inState = false;
		} else if (localName.equals("e2statetext") || localName.equals("e2resulttext")) {
			inStateText = false;
		} 
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char ch[], int start, int length) {
		String value = new String(ch, start, length);

		if (inState) {
			mResult.putOrConcat(SimpleResult.STATE, value);
		} else if(inStateText){
			mResult.putOrConcat(SimpleResult.STATE_TEXT, value);
		}
	}
}
