package net.reichholf.dreamdroid.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.roundToInt

/**
 * Clickable row that reports the tile's window top-left, for View [androidx.appcompat.widget.PopupMenu]
 * anchors. Stores the offset (not [androidx.compose.ui.layout.LayoutCoordinates]) so a later
 * click cannot read a detached node as (0, 0).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.listRowAnchoredClickable(
    onClick: (windowX: Int, windowY: Int) -> Unit,
    onLongClick: (windowX: Int, windowY: Int) -> Unit
): Modifier {
    var windowPos by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { windowPos = it.positionInWindow() }
        .combinedClickable(
            onClick = { onClick(windowPos.x.roundToInt(), windowPos.y.roundToInt()) },
            onLongClick = { onLongClick(windowPos.x.roundToInt(), windowPos.y.roundToInt()) }
        )
}
