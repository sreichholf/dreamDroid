/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;

/**
 * @author sreichholf
 * 
 */
//public class ExtendedHashMap extends HashMap<String, Object> implements Serializable {
public class ExtendedHashMap extends HashMap<String, Object> implements Parcelable {

	private static final long serialVersionUID = 1391952383782876012L;

	public ExtendedHashMap() {
		super();
	}
	
	public ExtendedHashMap(Parcel in){
		super();
		
		@SuppressWarnings("unchecked")
		HashMap<String, Object> map = (HashMap<String,Object>) in.readSerializable();
		putAll(map);
	}
	
	public ExtendedHashMap(HashMap<String,Object> map){
		super();
		if(map != null)
			putAll(map);
	}

	@Override
	public ExtendedHashMap clone() {
		return (ExtendedHashMap) super.clone();
	}
	
	public void putOrConcat(String prefix, String key, Object value){
		key = prefix.concat(key);
		putOrConcat(key, value);
	}
	
	/**
	 * Like standard put but concatenates the value if value is a
	 * "java.lang.String" and there already was a String value for the key
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public void putOrConcat(String key, Object value) {
		// Exceptions are very expensive in terms of runtime so let's try to
		// avoid them
		if (containsKey(key)) {
			try {
				if (value.getClass().equals(Class.forName("java.lang.String"))) {

					Object old = get(key);
					if ((old.getClass().equals(Class.forName("java.lang.String")))) {
						String oldval = (String) old;
						String val = (String) value;
						value = oldval.concat(val);
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
			}
		}
		put(key, value);
	}

	/**
	 * @param key
	 * @return
	 */
	public String getString(String key) {
		return (String) get(key);
	}
	
	/**
	 * @param key
	 * @param defaultString
	 * @return
	 */
	public String getString(String key, String defaultString) {
		String retVal = (String) get(key);
		if(retVal == null){
			retVal = defaultString;
		}
		return retVal;
	}

    public static final Parcelable.Creator<ExtendedHashMap> CREATOR
    = new Parcelable.Creator<ExtendedHashMap>() {
		public ExtendedHashMap createFromParcel(Parcel in) {
		    return new ExtendedHashMap(in);
		}
		
		public ExtendedHashMap[] newArray(int size) {
		    return new ExtendedHashMap[size];
		}
		};
	
	/* (non-Javadoc)
	 * @see android.os.Parcelable#describeContents()
	 */
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
	 */
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}
}
