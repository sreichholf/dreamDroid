package net.reichholf.dreamdroid.video

import kotlin.math.roundToInt

/**
 * AOSP accepts PiP aspect ratios from 0.418410 to 2.39
 * (`config_pictureInPictureMin/MaxAspectRatio`). Both bounds sit inside that range, so rounding
 * cannot cross them; a device that overrides the resources is covered by the caller.
 */
const val PIP_MIN_ASPECT_RATIO = 0.42f
const val PIP_MAX_ASPECT_RATIO = 2.38f

/** Denominator of the `Rational` that carries the aspect ratio to PiP. */
const val PIP_ASPECT_RATIO_SCALE = 10_000

/**
 * The picture's display aspect ratio for PiP, clamped into
 * [PIP_MIN_ASPECT_RATIO]..[PIP_MAX_ASPECT_RATIO]. Null while the engine has reported no size.
 */
fun pictureInPictureAspectRatio(width: Int, height: Int): Float? {
    if (width <= 0 || height <= 0) return null
    return (width.toFloat() / height).coerceIn(PIP_MIN_ASPECT_RATIO, PIP_MAX_ASPECT_RATIO)
}

/** A rectangle in window pixels, as `android.graphics.Rect` holds it. */
data class PipRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Where a [videoWidth] x [videoHeight] picture sits inside [container] when it is scaled to
 * fit and centered. This is the source rect hint for PiP, so its shape matches the aspect
 * ratio. Null while the engine has reported no size or the container is empty.
 */
fun pictureInPictureSourceRectHint(
    container: PipRect,
    videoWidth: Int,
    videoHeight: Int
): PipRect? {
    if (videoWidth <= 0 || videoHeight <= 0) return null
    if (container.width <= 0 || container.height <= 0) return null
    val scale = minOf(
        container.width.toDouble() / videoWidth,
        container.height.toDouble() / videoHeight
    )
    val width = (videoWidth * scale).roundToInt().coerceIn(1, container.width)
    val height = (videoHeight * scale).roundToInt().coerceIn(1, container.height)
    val left = container.left + (container.width - width) / 2
    val top = container.top + (container.height - height) / 2
    return PipRect(left, top, left + width, top + height)
}
