package net.reichholf.dreamdroid.video

object VideoPlayback {
    fun nextIndex(current: Int, size: Int): Int {
        if (current < 0 || size <= 0) {
            return -1
        }
        val next = current + 1
        return if (next >= size) 0 else next
    }

    fun previousIndex(current: Int, size: Int): Int {
        if (current < 0 || size <= 0) {
            return -1
        }
        return if (current == 0) size - 1 else current - 1
    }

    fun shouldTogglePause(isPlaying: Boolean, rate: Float, sameMedia: Boolean): Boolean {
        return sameMedia && isPlaying && rate == 1.0f
    }
}
