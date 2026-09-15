package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun ChangelogScreen(markdown: String, modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    val annotated = changelogMarkdownToAnnotatedString(
        markdown = markdown,
        heading2 = typography.titleMedium.toSpanStyle().copy(fontWeight = FontWeight.Bold),
        heading3 = typography.titleSmall.toSpanStyle().copy(fontWeight = FontWeight.Bold),
        body = typography.bodyMedium.toSpanStyle()
    )
    Text(
        text = annotated,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

internal fun changelogMarkdownToAnnotatedString(
    markdown: String,
    heading2: SpanStyle,
    heading3: SpanStyle,
    body: SpanStyle
): AnnotatedString {
    val blocks = parseChangelogMarkdown(markdown)
    return buildAnnotatedString {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                append('\n')
                if (block is ChangelogBlock.Heading2 || block is ChangelogBlock.Heading3) {
                    append('\n')
                }
            }
            withStyle(changelogSpanStyle(block, heading2, heading3, body)) {
                append(block.displayText())
            }
        }
    }
}

private fun changelogSpanStyle(
    block: ChangelogBlock,
    heading2: SpanStyle,
    heading3: SpanStyle,
    body: SpanStyle
): SpanStyle = when (block) {
    is ChangelogBlock.Heading2 -> heading2
    is ChangelogBlock.Heading3 -> heading3
    is ChangelogBlock.ListItem -> body
    is ChangelogBlock.Paragraph -> body
}
