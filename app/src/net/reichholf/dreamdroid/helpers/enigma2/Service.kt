package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.ExtendedHashMap

class Service : ExtendedHashMap() {
    companion object {
        const val KEY_NAME: String = "servicename"
        const val KEY_REFERENCE: String = "reference"

        enum class FLAGS(private val `val`: Int) {
            isDirectory(1),
            isMarker(64),
            isGroup(128),
            isLive(256);

            fun value(): Int = `val`
        }

        @JvmStatic
        fun getFlags(ref: String?): Int {
            var flags = 0
            if (ref.isNullOrEmpty()) return flags
            val f = try {
                ref.split(":")[1]
            } catch (_: ArrayIndexOutOfBoundsException) {
                return flags
            }
            return f.toInt()
        }

        @JvmStatic
        fun isDirectory(ref: String?): Boolean {
            return (getFlags(ref) and FLAGS.isDirectory.value()) == FLAGS.isDirectory.value()
        }

        @JvmStatic
        fun isBouquet(ref: String): Boolean = ref.startsWith("1:7:")

        @JvmStatic
        fun isMarker(ref: String?): Boolean {
            return (getFlags(ref) and FLAGS.isMarker.value()) == FLAGS.isMarker.value()
        }
    }
}
