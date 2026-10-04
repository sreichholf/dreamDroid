package net.reichholf.dreamdroid.data

import java.io.StringReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BackupDocumentsTest {
    @Test
    fun readsTextUpToTheLimit() {
        assertEquals("", StringReader("").readAtMost(4))
        assertEquals("abcd", StringReader("abcd").readAtMost(4))
    }

    @Test
    fun refusesTextOverTheLimit() {
        assertNull(StringReader("abcde").readAtMost(4))
    }

    @Test
    fun keepsReadingAcrossShortReads() {
        val trickle = object : java.io.Reader() {
            private val text = "abcdef"
            private var position = 0

            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (position == text.length) {
                    return -1
                }
                buffer[offset] = text[position++]
                return 1
            }

            override fun close() {}
        }

        assertEquals("abcdef", trickle.readAtMost(6))
    }
}
