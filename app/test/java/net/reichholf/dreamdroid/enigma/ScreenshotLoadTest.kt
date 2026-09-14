package net.reichholf.dreamdroid.enigma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotLoadTest {
    @Test
    fun htmlBodyIsNotSuccess() {
        val html = "<html><body>401 Unauthorized</body></html>".toByteArray()
        val result = screenshotPayloadResult(html, httpErrorText = null, fallbackError = "ERROR!")
        assertFalse(result.success)
        assertFalse(looksLikeScreenshotImage(html))
        assertNull(result.bytes)
        assertEquals("ERROR!", result.errorText)
    }

    @Test
    fun emptyBytesAreNotSuccess() {
        val result = screenshotPayloadResult(
            ByteArray(0),
            httpErrorText = null,
            fallbackError = "ERROR!"
        )
        assertFalse(result.success)
        assertFalse(looksLikeScreenshotImage(ByteArray(0)))
        assertNull(result.bytes)
        assertEquals("ERROR!", result.errorText)
    }

    @Test
    fun xmlBodyIsNotSuccess() {
        val xml = "<?xml version=\"1.0\"?><e2error>fail</e2error>".toByteArray()
        val result = screenshotPayloadResult(xml, httpErrorText = null, fallbackError = "ERROR!")
        assertFalse(result.success)
        assertEquals("ERROR!", result.errorText)
    }

    @Test
    fun jpegMagicIsSuccess() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val result = screenshotPayloadResult(jpeg, httpErrorText = null, fallbackError = "ERROR!")
        assertTrue(looksLikeScreenshotImage(jpeg))
        assertTrue(result.success)
        assertTrue(jpeg.contentEquals(result.bytes))
        assertNull(result.errorText)
    }

    @Test
    fun pngMagicIsSuccess() {
        val png = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00
        )
        assertTrue(looksLikeScreenshotImage(png))
        val result = screenshotPayloadResult(png, httpErrorText = null, fallbackError = "ERROR!")
        assertTrue(result.success)
        assertNull(result.errorText)
    }

    @Test
    fun httpErrorTextIsKeptWhenPayloadIsNotAnImage() {
        val result = screenshotPayloadResult(
            "not-an-image".toByteArray(),
            httpErrorText = "timeout",
            fallbackError = "ERROR!"
        )
        assertFalse(result.success)
        assertEquals("timeout", result.errorText)
    }
}
