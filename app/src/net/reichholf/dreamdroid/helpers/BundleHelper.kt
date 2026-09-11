package net.reichholf.dreamdroid.helpers

object BundleHelper {
    @JvmStatic
    fun toStringArrayList(strings: Array<CharSequence>): ArrayList<String> {
        val list = ArrayList<String>(strings.size)
        for (string in strings) {
            list.add(string.toString())
        }
        return list
    }

    @JvmStatic
    fun toCharSequenceArray(strings: ArrayList<String>): Array<CharSequence> {
        return Array(strings.size) { i -> strings[i] }
    }
}
