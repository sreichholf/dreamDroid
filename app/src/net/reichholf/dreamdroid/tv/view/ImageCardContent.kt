package net.reichholf.dreamdroid.tv.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.reichholf.dreamdroid.R

/** Shared now/next card text used by the Compose TV hub (Phase 3.1c-iv-f). */
@Composable
fun ImageCardContent(
    title: String,
    contentPrimary: String,
    nextStart: String,
    nextTitle: String,
    contentExpanded: Boolean,
    imageWidthPx: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cardWidth = if (imageWidthPx > 0) {
        with(density) { imageWidthPx.toDp() }
    } else {
        dimensionResource(R.dimen.card_width)
    }
    val titleColor = Color.White
    val bodyColor = Color.White.copy(alpha = 0.85f)
    val hasNext = nextStart.isNotEmpty() || nextTitle.isNotEmpty()
    val hasContent = contentPrimary.isNotEmpty() || hasNext
    Column(
        modifier = modifier
            .width(cardWidth)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasContent) {
            val body = buildAnnotatedString {
                if (contentPrimary.isNotEmpty()) {
                    append(contentPrimary)
                }
                if (hasNext) {
                    if (contentPrimary.isNotEmpty()) {
                        append('\n')
                    }
                    if (nextStart.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                        ) {
                            append(nextStart)
                        }
                        if (nextTitle.isNotEmpty()) {
                            append(' ')
                        }
                    }
                    if (nextTitle.isNotEmpty()) {
                        append(nextTitle)
                    }
                }
            }
            Text(
                text = body,
                color = bodyColor,
                fontSize = 12.sp,
                maxLines = if (contentExpanded) 4 else 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}
