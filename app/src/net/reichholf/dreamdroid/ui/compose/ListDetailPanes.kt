package net.reichholf.dreamdroid.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

const val LIST_DETAIL_DETAIL_PANE_TAG = "list_detail_detail_pane"
const val LIST_DETAIL_EXTRA_PANE_TAG = "list_detail_extra_pane"

/** Whether the window fits Material 3 list-detail as two panes (expanded width and up). */
@Composable
fun showsListDetailPanes(): Boolean = listDetailDirective().maxHorizontalPartitions > 1

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun listDetailDirective(): PaneScaffoldDirective =
    calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())

/**
 * Material 3 list-detail for a list whose detail is UI state the caller owns. Where the window
 * fits two panes ([showsListDetailPanes]), [detailContent] sits beside [list] in a
 * [ListDetailPaneScaffold], with [emptyDetail] while [detail] is null; without [emptyDetail]
 * the list takes the whole width until there is a detail. Narrower windows draw
 * [list] and a shown detail's [singlePaneDetail] over it: a bottom sheet, or the detail filling
 * the space as Material 3's single-pane list-detail does. Back clears a shown detail before it
 * leaves the screen; a detail that stands on its own (a form, a schedule) also gets a close
 * button in its [ListDetailPaneTopBar], while one that only describes a list item does not.
 * [list] keeps its state, such as its scroll position, when the window crosses between the two.
 * The pane's state starts fresh whenever [detailKey] of the detail changes.
 *
 * A detail that is itself a list can open one of its items in [extraPane], Material 3's extra
 * pane: on two panes the list makes room and the detail sits beside the extra pane, on a window
 * wide enough for three (1200dp and up) all three show, and back closes the extra pane first
 * ([onExtraDismiss]). [extraPane] is drawn only where the window fits two panes and while there
 * is a detail; elsewhere the caller shows that item its own way.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> ListDetailPanes(
    detail: T?,
    onDetailDismiss: () -> Unit,
    list: @Composable () -> Unit,
    emptyDetail: (@Composable () -> Unit)?,
    singlePaneDetail: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    detailKey: (T) -> Any = { it },
    extraPane: (@Composable () -> Unit)? = null,
    onExtraDismiss: () -> Unit = {},
    detailContent: @Composable (T) -> Unit
) {
    val currentList by rememberUpdatedState(list)
    val movableList = remember { movableContentOf { currentList() } }
    val directive = listDetailDirective()
    BackHandler(enabled = detail != null, onBack = onDetailDismiss)
    if (directive.maxHorizontalPartitions < 2) {
        Box(modifier) {
            // A detail drawn over the list hides it from accessibility services too.
            Box(if (detail != null) Modifier.clearAndSetSemantics {} else Modifier) {
                movableList()
            }
            detail?.let { singlePaneDetail(it) }
        }
        return
    }
    val extra = extraPane.takeIf { detail != null }
    // Registered after the detail's, so back closes the extra pane first.
    BackHandler(enabled = extra != null, onBack = onExtraDismiss)
    // A pane that hides with its content keeps showing that content while it animates out.
    val lastDetail = remember { LastDetail<T>() }
    if (detail != null) {
        lastDetail.value = detail
    }
    val lastExtra = remember { LastDetail<@Composable () -> Unit>() }
    if (extra != null) {
        lastExtra.value = extra
    }
    val paneDetail = detail ?: lastDetail.value.takeIf { emptyDetail == null }
    val paneExtra = extra ?: lastExtra.value
    val destination = ThreePaneScaffoldDestinationItem<Nothing>(
        when {
            extra != null -> ListDetailPaneScaffoldRole.Extra
            detail != null -> ListDetailPaneScaffoldRole.Detail
            else -> ListDetailPaneScaffoldRole.List
        }
    )
    val value = calculateThreePaneScaffoldValue(
        // Large windows fit three panes; a pane without content must still hide.
        maxHorizontalPartitions = when {
            detail == null && emptyDetail == null -> 1
            extra == null -> minOf(directive.maxHorizontalPartitions, 2)
            else -> directive.maxHorizontalPartitions
        },
        adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
        currentDestination = destination
    )
    ListDetailPaneScaffold(
        directive = directive,
        value = value,
        listPane = { AnimatedPane { movableList() } },
        detailPane = {
            AnimatedPane {
                // The list sits on the shell background; the detail gets its own container.
                PaneContainer(LIST_DETAIL_DETAIL_PANE_TAG) {
                    if (paneDetail == null) {
                        emptyDetail?.invoke()
                    } else {
                        key(detailKey(paneDetail)) { detailContent(paneDetail) }
                    }
                }
            }
        },
        // No extra pane slot for callers that never had one.
        extraPane = paneExtra?.let { content ->
            {
                AnimatedPane {
                    PaneContainer(LIST_DETAIL_EXTRA_PANE_TAG) { content() }
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun PaneContainer(tag: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag),
        content = content
    )
}

private class LastDetail<T : Any> {
    var value: T? = null
}

/** Placeholder for a detail pane with nothing selected. */
@Composable
fun ListDetailEmptyPane(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Top app bar of a detail pane that stands on its own, with a close button: the shell's bar
 * belongs to the list beside it. It sits on the pane's container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailPaneTopBar(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_close),
                    contentDescription = stringResource(R.string.close)
                )
            }
        },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

/** A detail filling a window too narrow for two panes, over the list. */
@Composable
fun ListDetailSinglePane(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        content = content
    )
}
