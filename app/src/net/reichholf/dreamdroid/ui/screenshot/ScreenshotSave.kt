package net.reichholf.dreamdroid.ui.screenshot

/**
 * Why a gallery save did not write an image. The Screenshot destination toasts
 * [net.reichholf.dreamdroid.R.string.error] for every non-null value.
 */
internal enum class ScreenshotGallerySaveError {
    EMPTY_BYTES,
    INSERT_FAILED,
    IO_EXCEPTION,
}

internal fun screenshotGallerySaveError(
    bytes: ByteArray,
    inserted: Boolean,
    ioFailed: Boolean,
): ScreenshotGallerySaveError? {
    return when {
        bytes.isEmpty() -> ScreenshotGallerySaveError.EMPTY_BYTES
        !inserted -> ScreenshotGallerySaveError.INSERT_FAILED
        ioFailed -> ScreenshotGallerySaveError.IO_EXCEPTION
        else -> null
    }
}
