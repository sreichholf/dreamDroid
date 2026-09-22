package net.reichholf.dreamdroid.ui.epg

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

/**
 * EPG search results as a Compose NavHost destination with Material 3 SearchBar.
 * Remount/reload is driven by [query] + host remount epoch for same-query resubmits.
 * List, draft, and the load job live on [EpgSearchViewModel]. The expanded flag stays here.
 */
@Composable
fun EpgSearchDestination(
    handle: PhoneNavHandle,
    query: String,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: EpgSearchViewModel = viewModel()
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(query, remountEpoch) {
        mutableStateOf(query.isEmpty())
    }
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.handle = handle
    dialogSession.context = context
    LaunchedEffect(query, remountEpoch) {
        viewModel.syncRoute(query, remountEpoch)
    }

    val toolbarTitle = if (viewModel.refreshing) {
        context.getString(R.string.loading)
    } else {
        context.getString(R.string.epg_search)
    }

    DisposableEffect(handle, dialogSession) {
        onDispose {
            dialogSession.dismissProgress()
        }
    }

    LaunchedEffect(toolbarTitle) {
        (context as? AppCompatActivity)?.title = toolbarTitle
    }

    DreamDroidPullRefresh(
        refreshing = viewModel.refreshing,
        onRefresh = { viewModel.reload() },
        enabled = query.isNotEmpty() && !expanded,
        modifier = modifier
    ) {
        EpgSearchScreen(
            query = viewModel.draftQuery,
            onQueryChange = viewModel::onDraftQueryChange,
            onSearch = { submitted ->
                val q = submitted.trim()
                if (q.isEmpty()) {
                    return@EpgSearchScreen
                }
                expanded = false
                handle.navigateToEpgSearch(q)
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            items = viewModel.listState.items,
            listState = viewModel.listState.listState,
            scrollEpoch = viewModel.listState.scrollEpoch,
            emptyMessage = viewModel.emptyMessage,
            onItemClick = { dialogSession.showDetail(it) }
        )
    }

    EpgEventDetailSheetHost(dialogSession)
}
