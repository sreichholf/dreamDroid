package net.reichholf.dreamdroid.video

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
