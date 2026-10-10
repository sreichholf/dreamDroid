package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListDetailPaneTopBar
import net.reichholf.dreamdroid.ui.text.asString

/**
 * A service's schedule in a detail pane beside the services list, with its own
 * [ListDetailPaneTopBar].
 */
@Composable
fun ServiceEpgPane(
    state: ServiceEpgUiState,
    onRefresh: () -> Unit,
    onEventClick: (Event) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        ListDetailPaneTopBar(title = state.title.asString(), onClose = onClose)
        DreamDroidPullRefresh(refreshing = state.refreshing, onRefresh = onRefresh) {
            ServiceEpgScreen(
                sections = state.sections,
                emptyMessage = state.emptyMessage?.asString(),
                onItemClick = onEventClick
            )
        }
    }
}
