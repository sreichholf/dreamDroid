package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Inset from the list edge so a slightly brighter row can read as a Gmail-style tile. */
val ListRowHorizontalInset = 8.dp

/** Half of the gap between tiles (top + bottom = 4.dp). */
val ListRowVerticalInset = 2.dp

const val LIST_ROW_TAG = "list_row"

/**
 * Gmail-style list tile: slightly brighter than the canvas, rounded, with a small
 * gutter left/right and between rows. The gutter (not a hairline) is the separator.
 *
 * The tile is the [ListItem] itself. [ListItem] merges its own semantics, so [onClick],
 * [onLongClick], and a toggle or selection passed in [modifier] all land on the node that
 * carries the row's label. [onLongClick] and [enabled] only apply together with [onClick].
 *
 * Material 3 1.4 has no interactive [ListItem]. The Material 3 Expressive overloads in the
 * 1.5 alphas (`ListItem(onClick, …)`, `ListItem(checked, onCheckedChange, …)`,
 * `ListItem(selected, onClick, …)`, and `SegmentedListItem`) replace the modifier approach.
 * Move this onto them once they ship in a stable release.
 */
@Composable
fun ListRow(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    // The gutter sits on a plain parent: ListItem's merged node takes its bounds from the start
    // of the ListItem modifier, so padding there would widen the row's node into the gutter.
    Box(modifier = Modifier.listRowGutter()) {
        ListItem(
            headlineContent = headlineContent,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LIST_ROW_TAG)
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (onClick != null) {
                        Modifier.combinedClickable(
                            enabled = enabled,
                            onLongClick = onLongClick,
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .then(modifier),
            overlineContent = overlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(containerColor = color)
        )
    }
}

/**
 * The [ListRow] tile for content that is not a [ListItem]. A [ListItem] inside merges its
 * own semantics apart from the tile, so an action in [modifier] would lose the row's label:
 * use [ListRow] for those rows.
 */
@Composable
fun ListRowSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .listRowGutter()
            .testTag(LIST_ROW_TAG)
            .then(modifier),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        content = { Column(content = content) }
    )
}

private fun Modifier.listRowGutter(): Modifier = fillMaxWidth()
    .padding(horizontal = ListRowHorizontalInset, vertical = ListRowVerticalInset)
