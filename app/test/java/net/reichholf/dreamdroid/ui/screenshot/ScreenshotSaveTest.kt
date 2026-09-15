package net.reichholf.dreamdroid.ui.screenshot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreenshotSaveTest {
    @Test
    fun emptyBytesSurfacesError() {
        assertEquals(
            ScreenshotGallerySaveError.EMPTY_BYTES,
            screenshotGallerySaveError(
                bytes = ByteArray(0),
                inserted = true,
                ioFailed = false
            )
        )
    }

    @Test
    fun failedInsertSurfacesError() {
        assertEquals(
            ScreenshotGallerySaveError.INSERT_FAILED,
            screenshotGallerySaveError(
                bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
                inserted = false,
                ioFailed = false
            )
        )
    }

    @Test
    fun ioFailureSurfacesError() {
        assertEquals(
            ScreenshotGallerySaveError.IO_EXCEPTION,
            screenshotGallerySaveError(
                bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
                inserted = true,
                ioFailed = true
            )
        )
    }

    @Test
    fun successfulWriteHasNoError() {
        assertNull(
            screenshotGallerySaveError(
                bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
                inserted = true,
                ioFailed = false
            )
        )
    }
}
