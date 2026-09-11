package net.reichholf.dreamdroid.helpers.enigma2

object Tag {
    @JvmStatic
    fun implodeTags(selectedTags: ArrayList<String>): String {
        val sb = StringBuilder()
        for (tag in selectedTags) {
            if (sb.isEmpty()) {
                sb.append(tag)
            } else {
                sb.append(' ').append(tag)
            }
        }
        return sb.toString()
    }
}
