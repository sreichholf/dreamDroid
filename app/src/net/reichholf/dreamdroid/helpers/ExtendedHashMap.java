/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Set;

/**
 * @author sreichholf
 */
public class ExtendedHashMap implements Serializable, Cloneable {

    protected HashMap<String, Object> mMap;
    private static final long serialVersionUID = 1391952383782876012L;

    public ExtendedHashMap() {
        mMap = new HashMap<>();
    }

    public ExtendedHashMap(HashMap<String, Object> map) {
        mMap = new HashMap<>();
        if (map != null)
            putAll(map);
    }

    public ExtendedHashMap(ExtendedHashMap map) {
        mMap = new HashMap<>();
        if (map != null)
            putAll(map.getHashMap());
    }

    @Override
    public ExtendedHashMap clone() {
        HashMap<String, Object> map = new HashMap<>(mMap);
        return new ExtendedHashMap(map);
    }

    public HashMap<String, Object> getHashMap() {
        return mMap;
    }

    public boolean containsKey(String key) {
        return mMap.containsKey(key);
    }

    public void put(String key, Object value) {
        mMap.put(key, value);
    }

    public Object get(String key) {
        return mMap.get(key);
    }

    public Object remove(String key) {
        return mMap.remove(key);
    }

    public void putAll(HashMap<String, Object> map) {
        mMap.putAll(map);
    }

    public void putAll(ExtendedHashMap map) {
        mMap.putAll(map.getHashMap());
    }

    public void clear() {
        mMap.clear();
    }

    public boolean isEmpty() {
        return mMap.isEmpty();
    }

    public int size() {
        return mMap.size();
    }

    public Set<String> keySet() {
        return mMap.keySet();
    }

    public void putOrConcat(String prefix, String key, Object value) {
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

    public String getString(String key) {
        return (String) get(key);
    }

    public String getString(String key, String defaultString) {
        String retVal = (String) get(key);
        if (retVal == null) {
            retVal = defaultString;
        }
        return retVal;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.valueOf(getString(key, "0"));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }
}
