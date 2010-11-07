package net.reichholf.dreamdroid.helpers.enigma2;

import java.util.ArrayList;

import net.reichholf.dreamdroid.dataProviders.SaxDataProvider;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.parsers.GenericSaxParser;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2TagHandler;

import org.apache.http.NameValuePair;

public class Tag {

	public static final String E2_TAG = "e2tag";

	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public static String getList(SimpleHttpClient shc) {
		if (shc.fetchPageContent(URIStore.TAGS, new ArrayList<NameValuePair>())) {
			return shc.getPageContentString();
		}

		return null;
	}

	/**
	 * @param xml
	 * @param list
	 * @return
	 */
	public static boolean parseList(String xml, ArrayList<String> list) {
		SaxDataProvider sdp = new SaxDataProvider(new GenericSaxParser());

		E2TagHandler handler = new E2TagHandler(list);
		sdp.getParser().setHandler(handler);

		if (sdp.parse(xml)) {
			return true;
		}

		return false;
	}
	
	/**
	 * @param selectedTags
	 * @return
	 */
	public static String implodeTags(ArrayList<String> selectedTags){
		String tags = "";
		for (String tag : selectedTags) {
			if ("".equals(tags)) {
				tags = tags.concat(tag);
			} else {
				tags = tags.concat(" ").concat(tag);
			}
		}
		
		return tags;
	}
}
