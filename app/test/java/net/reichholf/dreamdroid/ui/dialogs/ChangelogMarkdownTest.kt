package net.reichholf.dreamdroid.ui.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ChangelogMarkdownTest {
    @Test
    fun readChangelogUtf8ReadsText() {
        val input = ByteArrayInputStream("hello changelog".toByteArray(Charsets.UTF_8))
        assertEquals("hello changelog", readChangelogUtf8(input))
    }

    @Test
    fun readChangelogUtf8ReturnsNullOnIoFailure() {
        val failing = object : InputStream() {
            override fun read(): Int {
                throw IOException("boom")
            }
        }
        assertNull(readChangelogUtf8(failing))
    }
}
