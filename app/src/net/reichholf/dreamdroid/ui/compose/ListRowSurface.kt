package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Inset from the list edge so a slightly brighter row can read as a Gmail-style tile. */
val ListRowHorizontalInset = 8.dp

/** Half of the gap between tiles (top + bottom = 4.dp). */
val ListRowVerticalInset = 2.dp

const val LIST_ROW_SURFACE_TAG = "list_row_surface"

@Composable
fun listRowItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/**
 * Gmail-style list tile: slightly brighter than the canvas, rounded, with a small
 * gutter left/right and between rows. The gutter (not a hairline) is the separator.
 */
@Composable
fun ListRowSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ListRowHorizontalInset,
                vertical = ListRowVerticalInset
            )
            .testTag(LIST_ROW_SURFACE_TAG)
            .then(modifier),
        color = color,
        shape = MaterialTheme.shapes.medium,
        content = { Column(content = content) }
    )
}
