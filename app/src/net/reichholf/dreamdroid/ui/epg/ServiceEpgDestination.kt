package net.reichholf.dreamdroid.ui.epg

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Phase 2.7f: per-service EPG as a direct Compose NavHost destination.
 * The list, refresh, and load job live on [ServiceEpgViewModel].
 */
@Composable
fun ServiceEpgDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: ServiceEpgViewModel = viewModel()
) {
    // Prefer Compose BackHandler so system Back pops to hub before MainActivity leave-confirm.
    BackHandler {
        handle.popNavBackStack()
    }
    val context = LocalContext.current
    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.handle = handle
    dialogSession.context = context

    DisposableEffect(handle, dialogSession) {
        onDispose {
            dialogSession.dismissProgress()
        }
    }

    val connectionSession =
        SessionConnectionHolder.shared.status.collectAsState().value.session
    LaunchedEffect(viewModel, connectionSession) {
        if (viewModel.serviceRef.isEmpty()) {
            handle.popNavBackStack()
        } else {
            viewModel.bindSession(connectionSession)
        }
    }

    val toolbarTitle = if (viewModel.refresh.isRefreshing) {
        stringResource(R.string.loading)
    } else {
        "${stringResource(R.string.epg)} - ${viewModel.serviceName}"
    }
    LaunchedEffect(toolbarTitle) {
        (context as? AppCompatActivity)?.title = toolbarTitle
    }

    DreamDroidPullRefresh(
        refreshing = viewModel.refresh.isRefreshing,
        onRefresh = { viewModel.reload(forceRefresh = true) },
        enabled = viewModel.refresh.enabled,
        modifier = modifier
    ) {
        ServiceEpgScreen(
            items = viewModel.listState.items,
            emptyMessage = viewModel.emptyMessage,
            onItemClick = { dialogSession.showDetail(it) }
        )
    }

    EpgEventDetailSheetHost(dialogSession)
}
