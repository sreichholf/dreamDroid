package net.reichholf.dreamdroid.helpers;

import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by reichi on 18/08/15.
 */
public class NameValuePair {
	private String mKey;
	private String mValue;
	public NameValuePair(String key, String value)
	{
		mKey = key;
		mValue = value;
	}

	public String key(){
		return mKey;
	}

	public String value() {
		if(mValue == null)
			return "";
		return mValue;
	}

	public static String toString(NameValuePair pair) {
		String value = "";
		try {
			value = URLEncoder.encode(pair.value(), "utf-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return String.format("%s=%s", pair.key(), value);
	}

	public static String toString(List<NameValuePair> pairs) {
		ArrayList<String> params = new ArrayList<>();
		for(NameValuePair pair : pairs) {
			params.add(toString(pair));
		}
		return TextUtils.join("&", params);
	}
}
