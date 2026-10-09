package net.reichholf.dreamdroid.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

const val LIST_DETAIL_DETAIL_PANE_TAG = "list_detail_detail_pane"

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
 * [ListDetailPaneScaffold], with [emptyDetail] while [detail] is null; back clears a shown
 * detail before it leaves the screen. Narrower windows draw [list] alone and hand a shown
 * detail to [singlePaneDetail] (a bottom sheet, say). [list] keeps its state, such as its scroll
 * position, when the window crosses between the two.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> ListDetailPanes(
    detail: T?,
    onDetailDismiss: () -> Unit,
    list: @Composable () -> Unit,
    emptyDetail: @Composable () -> Unit,
    singlePaneDetail: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    detailContent: @Composable (T) -> Unit
) {
    val currentList by rememberUpdatedState(list)
    val movableList = remember { movableContentOf { currentList() } }
    val directive = listDetailDirective()
    if (directive.maxHorizontalPartitions < 2) {
        Box(modifier) { movableList() }
        detail?.let { singlePaneDetail(it) }
        return
    }
    val destination = ThreePaneScaffoldDestinationItem<Nothing>(
        if (detail == null) ListDetailPaneScaffoldRole.List else ListDetailPaneScaffoldRole.Detail
    )
    val value = calculateThreePaneScaffoldValue(
        maxHorizontalPartitions = directive.maxHorizontalPartitions,
        adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
        currentDestination = destination
    )
    BackHandler(enabled = detail != null, onBack = onDetailDismiss)
    ListDetailPaneScaffold(
        directive = directive,
        value = value,
        listPane = { AnimatedPane { movableList() } },
        detailPane = {
            AnimatedPane {
                // The list sits on the shell background; the detail gets its own container.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(LIST_DETAIL_DETAIL_PANE_TAG)
                ) {
                    if (detail == null) {
                        emptyDetail()
                    } else {
                        // A different item starts with fresh state (scroll position and so on).
                        key(detail) { detailContent(detail) }
                    }
                }
            }
        },
        modifier = modifier
    )
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
