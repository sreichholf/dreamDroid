package net.reichholf.dreamdroid.tv.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlin.math.floor

internal const val FITTED_ELLIPSIS_TEXT_TAG = "fitted_ellipsis_text"

internal val FittedMaxLines = SemanticsPropertyKey<Int>("FittedMaxLines")

internal var SemanticsPropertyReceiver.fittedMaxLines by FittedMaxLines

/** Whole lines that fit in [maxHeightPx] for a uniform [lineHeightPx]. */
internal fun fittedLineCountFromHeight(maxHeightPx: Float, lineHeightPx: Float): Int {
    if (maxHeightPx <= 0f || lineHeightPx <= 0f) {
        return 0
    }
    return floor(maxHeightPx / lineHeightPx).toInt()
}

/**
 * Body text that uses only fully visible lines in the incoming height, then ellipsizes.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FittedEllipsisText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        val maxLines =
            if (text.isEmpty() || maxWidthPx <= 0 || maxHeightPx <= 0) {
                0
            } else {
                val probe =
                    measurer.measure(
                        text = "X",
                        style = style,
                        constraints = Constraints(maxWidth = maxWidthPx)
                    )
                val lineHeightPx =
                    if (probe.lineCount > 0) {
                        probe.getLineBottom(0) - probe.getLineTop(0)
                    } else {
                        probe.size.height.toFloat()
                    }
                fittedLineCountFromHeight(maxHeightPx.toFloat(), lineHeightPx)
            }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FITTED_ELLIPSIS_TEXT_TAG)
                .semantics { fittedMaxLines = maxLines }
        ) {
            if (maxLines > 0) {
                Text(
                    text = text,
                    style = style,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
