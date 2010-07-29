/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.parsers;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.util.Log;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.dataProviders.interfaces.DataParser;

/**
 * @author sreichholf
 * 
 */
public class GenericSaxParser implements DataParser {
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.reichholf.dreamdroid.dataProviders.interfaces.DataParser#parse(java
	 * .lang.String)
	 */
	@Override
	public boolean parse(String input) {
		try {
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
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			Log.e(DreamDroid.LOG_TAG, e.toString());
			mError = true;
			mErrorText = e.toString();
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			Log.e(DreamDroid.LOG_TAG, e.toString());
			mError = true;
			mErrorText = e.toString();
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.e(DreamDroid.LOG_TAG, e.toString());
			mError = true;
			mErrorText = e.toString();
			e.printStackTrace();
		}
		
		return false;
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
