/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.dataProviders.interfaces.DataParser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.util.Log;

/**
 * @author sreichholf
 * 
 */
public class GenericSaxParser implements DataParser {
	private static String LOG_TAG = GenericSaxParser.class.getSimpleName();
	private DefaultHandler mHandler;
	private boolean mError;
	private String mErrorText;

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
		/*
		Pattern ctrl = Pattern.compile("\\p{C}");
		Matcher m = ctrl.matcher(in);
		HashSet<Integer> cchars = new HashSet<>();

		while(m.find()){
			String s = m.group();
			int val = (int) s.charAt(0);
			if(val != 0x000a && val != 0x0009)
				cchars.add(Integer.valueOf(val));
		}
		for(Integer c : cchars)
			Log.w(LOG_TAG, String.format("Invalid Hex Control Character in xml: %04x", c.intValue()));
		*/
		if(aggressive)
			return in.replaceAll("\\p{C}", "").replaceAll("&nbsp;", " ");
		else
			return in.replaceAll("\\p{Co}|\\p{Cs}|\\p{Cn}|", "").replaceAll("\\u008A", "\n").replaceAll("&nbsp;", " ");
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
			Log.e(DreamDroid.LOG_TAG, e.toString());
			if(isRetry) {
				mError = true;
				mErrorText = e.toString();
			} else {
				Log.w(DreamDroid.LOG_TAG, "Retrying with aggressive character filtering!");
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
