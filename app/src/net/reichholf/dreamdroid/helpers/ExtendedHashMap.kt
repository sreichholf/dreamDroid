/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers

import java.io.Serializable

/**
 * @author sreichholf
 */
open class ExtendedHashMap : Serializable, Cloneable {
    @JvmField
    protected var mMap: HashMap<String, Any?>

    constructor() {
        mMap = HashMap()
    }

    constructor(map: HashMap<String, Any?>?) {
        mMap = HashMap()
        if (map != null) putAll(map)
    }

    constructor(map: ExtendedHashMap?) {
        mMap = HashMap()
        if (map != null) putAll(map.getHashMap())
    }

    public override fun clone(): ExtendedHashMap {
        val map = HashMap(mMap)
        return ExtendedHashMap(map)
    }

    fun getHashMap(): HashMap<String, Any?> = mMap

    fun containsKey(key: String?): Boolean = key != null && mMap.containsKey(key)

    fun put(key: String?, value: Any?) {
        if (key != null) mMap[key] = value
    }

    operator fun get(key: String?): Any? = mMap[key]

    fun remove(key: String?): Any? = mMap.remove(key)

    fun putAll(map: HashMap<String, Any?>) {
        mMap.putAll(map)
    }

    fun putAll(map: ExtendedHashMap) {
        mMap.putAll(map.getHashMap())
    }

    fun clear() {
        mMap.clear()
    }

    fun isEmpty(): Boolean = mMap.isEmpty()

    fun size(): Int = mMap.size

    fun keySet(): Set<String> = mMap.keys

    fun putOrConcat(prefix: String, key: String, value: Any) {
        val k = prefix + key
        putOrConcat(k, value)
    }

    /**
     * Like standard put but concatenates the value if value is a
     * "java.lang.String" and there already was a String value for the key
     */
    fun putOrConcat(key: String?, value: Any) {
        // Exceptions are very expensive in terms of runtime so let's try to
        // avoid them
        if (containsKey(key)) {
            try {
                if (value.javaClass == Class.forName("java.lang.String")) {
                    val old = get(key)
                    if (old != null && old.javaClass == Class.forName("java.lang.String")) {
                        val oldval = old as String
                        val `val` = value as String
                        put(key, oldval + `val`)
                        return
                    }
                }
            } catch (_: Exception) {
                // TODO Auto-generated catch block
            }
        }
        put(key, value)
    }

    fun getString(key: String?): String? = get(key) as String?

    fun getString(key: String?, defaultString: String?): String? {
        var retVal = get(key) as String?
        if (retVal == null) {
            retVal = defaultString
        }
        return retVal
    }

    fun getInt(key: String?, def: Int): Int {
        return try {
            Integer.valueOf(getString(key, "0"))
        } catch (_: NumberFormatException) {
            def
        }
    }

    fun getInt(key: String?): Int = getInt(key, 0)

    companion object {
        private const val serialVersionUID: Long = 1391952383782876012L
    }
}
