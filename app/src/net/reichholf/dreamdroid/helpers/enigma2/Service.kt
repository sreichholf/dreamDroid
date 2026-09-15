package net.reichholf.dreamdroid.helpers.enigma2

object Service {
    const val KEY_NAME: String = "servicename"
    const val KEY_REFERENCE: String = "reference"

    enum class FLAGS(private val flag: Int) {
        IS_DIRECTORY(1),
        IS_MARKER(64),
        IS_GROUP(128),
        IS_LIVE(256);

        fun value(): Int = flag
    }

    fun getFlags(ref: String?): Int {
        if (ref.isNullOrEmpty()) return 0
        val f = try {
            ref.split(":")[1]
        } catch (_: ArrayIndexOutOfBoundsException) {
            return 0
        }
        return f.toInt()
    }

    fun isDirectory(ref: String?): Boolean {
        if (ref.isNullOrEmpty()) return false
        if ((getFlags(ref) and FLAGS.IS_DIRECTORY.value()) == FLAGS.IS_DIRECTORY.value()) {
            return true
        }
        // Provider / satellite / bouquet path nodes sometimes omit the directory flag bit.
        return ref.contains("FROM PROVIDERS") ||
            ref.contains("FROM SATELLITES") ||
            ref.contains("FROM BOUQUET")
    }

    fun isBouquet(ref: String): Boolean = ref.startsWith("1:7:")

    fun isMarker(ref: String?): Boolean =
        (getFlags(ref) and FLAGS.IS_MARKER.value()) == FLAGS.IS_MARKER.value()
}
