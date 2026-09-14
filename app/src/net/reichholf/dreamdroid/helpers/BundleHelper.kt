package net.reichholf.dreamdroid.helpers

object BundleHelper {
    fun toStringArrayList(strings: Array<CharSequence>): ArrayList<String> {
        val list = ArrayList<String>(strings.size)
        for (string in strings) {
            list.add(string.toString())
        }
        return list
    }

    fun toCharSequenceArray(strings: ArrayList<String>): Array<CharSequence> =
        Array(strings.size) { i ->
            strings[i]
        }
}
