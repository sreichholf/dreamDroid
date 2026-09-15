package net.reichholf.dreamdroid.ui.screenshot

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal const val SCREENSHOT_MIN_SCALE = 1f
internal const val SCREENSHOT_MAX_SCALE = 5f

internal val ScreenshotZoomScale = SemanticsPropertyKey<Float>("ScreenshotZoomScale")

internal var SemanticsPropertyReceiver.screenshotZoomScale by ScreenshotZoomScale

internal data class ScreenshotZoomTransform(val scale: Float, val offset: Offset)

/**
 * Pinch/pan in layout pixels. [graphicsLayer] scales about the composable center,
 * so the centroid is mapped through that origin before pan is applied.
 */
internal fun applyScreenshotZoom(
    scale: Float,
    offset: Offset,
    centroid: Offset,
    pan: Offset,
    zoom: Float,
    containerSize: Size,
    imageWidth: Int,
    imageHeight: Int
): ScreenshotZoomTransform {
    if (containerSize.width <= 0f || containerSize.height <= 0f) {
        return ScreenshotZoomTransform(scale, offset)
    }
    val newScale = (scale * zoom).coerceIn(SCREENSHOT_MIN_SCALE, SCREENSHOT_MAX_SCALE)
    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
    val zoomed = offset + (centroid - center) * (scale - newScale) + pan
    return ScreenshotZoomTransform(
        scale = newScale,
        offset = clampScreenshotOffset(
            offset = zoomed,
            scale = newScale,
            containerSize = containerSize,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    )
}

internal fun clampScreenshotOffset(
    offset: Offset,
    scale: Float,
    containerSize: Size,
    imageWidth: Int,
    imageHeight: Int
): Offset {
    val fitted = fittedImageSize(containerSize, imageWidth, imageHeight)
    val maxX = ((fitted.width * scale - containerSize.width) / 2f).coerceAtLeast(0f)
    val maxY = ((fitted.height * scale - containerSize.height) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

@Composable
internal fun ZoomableScreenshot(
    bitmap: Bitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember(bitmap) { mutableFloatStateOf(SCREENSHOT_MIN_SCALE) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                containerSize = Size(size.width.toFloat(), size.height.toFloat())
            }
            .semantics {
                this.contentDescription = contentDescription
                screenshotZoomScale = scale
            }
            .pointerInput(bitmap, containerSize) {
                if (bitmap == null) {
                    return@pointerInput
                }
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val next = applyScreenshotZoom(
                        scale = scale,
                        offset = offset,
                        centroid = centroid,
                        pan = pan,
                        zoom = zoom,
                        containerSize = containerSize,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height
                    )
                    scale = next.scale
                    offset = next.offset
                }
            }
    ) {
        if (bitmap != null) {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}

private fun fittedImageSize(container: Size, imageWidth: Int, imageHeight: Int): Size {
    if (imageWidth <= 0 || imageHeight <= 0 ||
        container.width <= 0f || container.height <= 0f
    ) {
        return Size.Zero
    }
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    val boxAspect = container.width / container.height
    return if (imageAspect > boxAspect) {
        Size(container.width, container.width / imageAspect)
    } else {
        Size(container.height * imageAspect, container.height)
    }
}
