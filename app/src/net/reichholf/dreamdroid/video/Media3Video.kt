package net.reichholf.dreamdroid.video

/**
 * The picture size [Media3Engine] reports to the overlay. Kept apart from the player listener
 * so the sample-aspect-ratio arithmetic has one home.
 */
object Media3Video {
    /** The scale the sample aspect ratio is reported in, so it fits an Int. */
    const val SAR_SCALE: Int = 1000

    /**
     * The layout for a picture of [width] × [height] with a sample aspect ratio of
     * [pixelWidthHeightRatio]. Null until the decoder reports a size: a stream that has not
     * started has none.
     */
    fun layout(width: Int, height: Int, pixelWidthHeightRatio: Float): VideoLayout? {
        if (width <= 0 || height <= 0) {
            return null
        }
        return VideoLayout(
            width = width,
            height = height,
            visibleWidth = width,
            visibleHeight = height,
            sarNum = (pixelWidthHeightRatio * SAR_SCALE).toInt(),
            sarDen = SAR_SCALE
        )
    }
}
