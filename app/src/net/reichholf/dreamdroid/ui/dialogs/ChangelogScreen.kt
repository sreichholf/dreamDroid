package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChangelogScreen(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseChangelogMarkdown(markdown) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            ChangelogBlockItem(block = block, isFirst = index == 0)
        }
    }
}

@Composable
private fun ChangelogBlockItem(block: ChangelogBlock, isFirst: Boolean) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    when (block) {
        is ChangelogBlock.Heading2 -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.titleLarge,
                color = onSurface,
                modifier = Modifier.padding(top = if (isFirst) 0.dp else 8.dp)
            )
        }

        is ChangelogBlock.Heading3 -> {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        is ChangelogBlock.ListItem -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurfaceVariant
                )
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is ChangelogBlock.Paragraph -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge,
                color = onSurface
            )
        }
    }
}
