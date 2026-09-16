package net.reichholf.dreamdroid.ui.signal

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val SIGNAL_SNR_GAUGE_TAG = "signal_snr_gauge"

val SignalGaugeNeedleColorArgb =
    SemanticsPropertyKey<Int>("SignalGaugeNeedleColorArgb")

var SemanticsPropertyReceiver.signalGaugeNeedleColorArgb by SignalGaugeNeedleColorArgb

val SignalGaugeValueColorArgb =
    SemanticsPropertyKey<Int>("SignalGaugeValueColorArgb")

var SemanticsPropertyReceiver.signalGaugeValueColorArgb by SignalGaugeValueColorArgb

/** Matches simple-gauge-android HalfGauge: 120° sweep starting at 210°. */
private const val ArcStartAngle = 210f
private const val ArcSweepAngle = 120f

private data class GaugeRange(val color: Color, val from: Float, val to: Float)

@Composable
fun SignalGauge(percent: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val onSurface = scheme.onSurface
    val onSurfaceArgb = onSurface.toArgb()
    val ranges = listOf(
        GaugeRange(scheme.error, 0f, 50f),
        GaugeRange(scheme.tertiary, 50f, 65f),
        GaugeRange(scheme.primary, 65f, 80f),
        GaugeRange(scheme.primaryContainer, 80f, 100f)
    )
    val value = percent.coerceIn(0, 100)
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.headlineMedium.copy(color = onSurface)
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = onSurface)
    Canvas(
        modifier = modifier
            .testTag(SIGNAL_SNR_GAUGE_TAG)
            .semantics {
                contentDescription = "SNR $value%"
                signalGaugeNeedleColorArgb = onSurfaceArgb
                signalGaugeValueColorArgb = onSurfaceArgb
            }
    ) {
        val labelSpace = 20.dp.toPx()
        val strokeWidth = min(size.width, size.height) * 0.16f
        val maxRadius = min(
            size.width / 2f - labelSpace,
            (size.height - labelSpace) * 0.72f
        )
        val radius = maxRadius - strokeWidth / 2f
        val center = Offset(x = size.width / 2f, y = size.height * 0.58f)
        drawSnrRanges(
            center = center,
            radius = radius,
            strokeWidth = strokeWidth,
            ranges = ranges
        )
        drawNeedle(
            center = center,
            radius = radius,
            strokeWidth = strokeWidth,
            percent = value,
            color = onSurface
        )
        drawCenteredText(
            measurer = textMeasurer,
            text = value.toString(),
            style = valueStyle,
            color = onSurface,
            center = Offset(center.x, center.y + radius * 0.28f)
        )
        val labelRadius = radius + strokeWidth * 0.55f
        drawCenteredText(
            measurer = textMeasurer,
            text = "0",
            style = labelStyle,
            color = onSurface,
            center = polar(center, labelRadius, ArcStartAngle)
        )
        drawCenteredText(
            measurer = textMeasurer,
            text = "100",
            style = labelStyle,
            color = onSurface,
            center = polar(center, labelRadius, ArcStartAngle + ArcSweepAngle)
        )
    }
}

private fun DrawScope.drawSnrRanges(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    ranges: List<GaugeRange>
) {
    val diameter = radius * 2f
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(diameter, diameter)
    val style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    for (range in ranges) {
        val start = ArcStartAngle + ArcSweepAngle * (range.from / 100f)
        val sweep = ArcSweepAngle * ((range.to - range.from) / 100f) + 0.5f
        drawArc(
            color = range.color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = style
        )
    }
}

private fun DrawScope.drawNeedle(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    percent: Int,
    color: Color
) {
    val fraction = percent / 100f
    val angle = ArcStartAngle + ArcSweepAngle * fraction
    val needleLength = radius - strokeWidth / 4f
    drawLine(
        color = color,
        start = center,
        end = polar(center, needleLength, angle),
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawCircle(color = color, radius = radius * 0.045f, center = center)
}

private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    center: Offset
) {
    val layout = measurer.measure(text = text, style = style)
    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = Offset(
            x = center.x - layout.size.width / 2f,
            y = center.y - layout.size.height / 2f
        )
    )
}

private fun polar(center: Offset, radius: Float, angleDegrees: Float): Offset {
    val radians = angleDegrees * (PI.toFloat() / 180f)
    return Offset(
        x = center.x + radius * cos(radians),
        y = center.y + radius * sin(radians)
    )
}
