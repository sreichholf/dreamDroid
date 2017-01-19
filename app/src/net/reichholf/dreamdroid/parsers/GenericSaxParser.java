/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers;

import android.util.Log;

import net.reichholf.dreamdroid.dataProviders.interfaces.DataParser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * @author sreichholf
 * 
 */
public class GenericSaxParser implements DataParser {
	private static String LOG_TAG = GenericSaxParser.class.getSimpleName();
	private DefaultHandler mHandler;
	private boolean mError;
	private String mErrorText;

	static Pattern sControlPatternAggressive = Pattern.compile("\\p{C}");

	/**
	 * 
	 */
	public GenericSaxParser() {
		mError = false;
	}

	/**
	 * @param h
	 */
	public GenericSaxParser(DefaultHandler h) {
		mHandler = h;
		mError = false;
	}

	/**
	 * @param h
	 */
	public void setHandler(DefaultHandler h) {
		mHandler = h;
	}

	/**
	 * @return
	 */
	public DefaultHandler getHandler() {
		return mHandler;
	}


	protected String stripNonValidXMLCharacters(String in, boolean aggressive) {
		if(aggressive)
			return sControlPatternAggressive.matcher(in).replaceAll("").replace("&nbsp;", " ");
		else
			return stripControlCharacters(in).replace("\u008A", "\n").replace("&nbsp;", " ");
	}

	/*
	 * this is based on https://github.com/GreyCat/java-string-benchmark/blob/master/src/ru/greycat/algorithms/strip/RatchetFreak2EdStaub1GreyCat1.java
	 * and is about a zillion lightyears faster than replaceAll... (defeats noticable lag between load finish and parse finish)
	 */
	public String stripControlCharacters(String s) {
		int length = s.length();
		char[] oldChars = new char[length +1];
		s.getChars(0, length, oldChars, 0);
		oldChars[length] = '\0'; // avoiding explicit bound check in while

		int newLen = 0;
		// find first non-printable,
		// if there are none it ends on the null char I appended
		while (true) {
			++newLen;
			char ch = oldChars[newLen];
			if(! (ch > ' ' || Character.isWhitespace(ch)) )
				break;
		}

		for (int j = newLen; j < length; j++) {
			char ch = oldChars[j];
			if (ch > ' ' || Character.isWhitespace(ch)) {
				oldChars[newLen] = ch; // the while avoids repeated overwriting here when newLen==j
				newLen++;
			}
		}
		return new String(oldChars, 0, newLen);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.dataProviders.interfaces.DataParser#parse(java
	 * .lang.String)
	 */

	protected boolean parse(String input, boolean isRetry) {
		try {
			input = stripNonValidXMLCharacters(input, isRetry);
			mError = false;
			mErrorText = null;
			// InputSource is = new InputSource(input);
			ByteArrayInputStream bais = new ByteArrayInputStream(input.getBytes());
			InputSource is = new InputSource();
			is.setByteStream(bais);

			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setValidating(false);
			SAXParser sp = spf.newSAXParser();


			/* Get the XMLReader of the SAXParser we created. */
			XMLReader xr = sp.getXMLReader();
			/* Create a new ContentHandler and apply it to the XML-Reader */
			xr.setContentHandler(mHandler);
			xr.parse(is);

			return true;
		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			Log.e(LOG_TAG, e.toString());
			if(isRetry) {
				mError = true;
				mErrorText = e.toString();
			} else {
				Log.w(LOG_TAG, "Retrying with aggressive character filtering!");
				return parse(input, true);
			}
		}
		return false;
	}

	@Override
	public boolean parse(String input) {
		return parse(input, false);
	}
	
	/**
	 * @return
	 */
	public boolean hasError(){
		return mError;
	}
	
	/**
	 * @return
	 */
	public String getErrorText(){
		return mErrorText;
	}
}
