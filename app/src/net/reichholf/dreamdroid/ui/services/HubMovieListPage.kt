package net.reichholf.dreamdroid.ui.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import java.io.File
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.ui.movies.MovieDetailModalSheet
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.text.asString

/**
 * One Movies hub location. The ViewModel is keyed by [location] on the hub back-stack
 * entry, so a tab change keeps the loaded list and the tag filter. Online-only taps go
 * through [runOnlineOnly], which explains a blocked tap.
 */
@Composable
fun HubMovieListPage(
    handle: PhoneNavHandle,
    location: String,
    modifier: Modifier = Modifier,
    viewModel: HubMovieListViewModel =
        hiltViewModel<HubMovieListViewModel, HubMovieListViewModel.Factory>(
            key = "hub-movie:$location"
        ) { factory -> factory.create(location) }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    val tagsLabel = stringResource(R.string.tags)
    BindShellTopBarActions(
        remember(viewModel, tagsLabel) {
            listOf(
                ShellTopBarAction(
                    id = Statics.ITEM_TAGS,
                    label = tagsLabel,
                    iconRes = R.drawable.ic_action_tags,
                    onClick = viewModel::onPickTags
                )
            )
        }
    )

    val open = uiState.open
    LaunchedEffect(open) {
        if (open == null) {
            return@LaunchedEffect
        }
        try {
            context.startActivity(open.intent(context))
            viewModel.onOpened()
        } catch (_: ActivityNotFoundException) {
            viewModel.onOpenFailed()
        }
    }

    HubMovieListScreen(
        state = uiState,
        onRefresh = viewModel::reload,
        onItemClick = { item, isLong ->
            val instantZap = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, false)
            if (instantZap != isLong) {
                handle.runOnlineOnly { viewModel.zap(item.index) }
            } else {
                viewModel.onItemMenu(item.index)
            }
        },
        onMenuAction = { action ->
            if (action.onlineOnly) {
                handle.runOnlineOnly { viewModel.onMenuAction(action) }
            } else {
                viewModel.onMenuAction(action)
            }
        },
        onMenuDismiss = viewModel::onMenuDismiss,
        onDetailDismiss = viewModel::onDetailDismissed,
        onTagsPicked = viewModel::onTagsPicked,
        onTagPickerDismiss = viewModel::onTagPickerDismissed,
        onDeleteConfirm = {
            handle.runOnlineOnly(viewModel::onDeleteConfirmed)
            viewModel.onDeleteDismissed()
        },
        onDeleteDismiss = viewModel::onDeleteDismissed,
        modifier = modifier
    )
}

/** [HubMovieListPage] without its ViewModel: the list, its row menu, and its dialogs. */
@Composable
fun HubMovieListScreen(
    state: HubMovieListUiState,
    onRefresh: () -> Unit,
    onItemClick: (item: MovieListItem, isLong: Boolean) -> Unit,
    onMenuAction: (MovieRowAction) -> Unit,
    onMenuDismiss: () -> Unit,
    onDetailDismiss: () -> Unit,
    onTagsPicked: (indices: List<Int>) -> Unit,
    onTagPickerDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DreamDroidPullRefresh(
        refreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        if (state.items.isEmpty()) {
            ListEmptyState(
                loading = state.refreshing,
                message = state.emptyMessage?.asString(),
                onRetry = onRefresh
            )
        } else {
            MovieListScreen(
                items = state.items,
                onItemClick = { onItemClick(it, false) },
                onItemLongClick = { onItemClick(it, true) },
                menu = state.menu,
                onMenuAction = onMenuAction,
                onMenuDismiss = onMenuDismiss
            )
        }
    }

    state.detail?.let { content ->
        MovieDetailModalSheet(content = content, onDismiss = onDetailDismiss)
    }

    state.tagPicker?.let { tags ->
        val checked = remember(tags, state.selectedTags) {
            BooleanArray(tags.size) { i -> tags[i] in state.selectedTags }
        }
        MultiChoiceAlertDialog(
            title = stringResource(R.string.choose_tags),
            items = tags,
            initialChecked = checked,
            onDismiss = onTagPickerDismiss,
            onConfirm = onTagsPicked
        )
    }

    state.deleteConfirm?.let { title ->
        ConfirmAlertDialog(
            title = title,
            message = stringResource(R.string.delete_confirm),
            onDismiss = onDeleteDismiss,
            onConfirm = onDeleteConfirm,
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }

    IndeterminateProgressHost(state.progress?.let { IndeterminateProgressState(it.asString()) })
}

private fun MovieOpen.intent(context: Context): Intent = when (this) {
    is MovieOpen.Stream -> IntentFactory.getStreamFileIntent(context, url, movie.title, movie)
    is MovieOpen.Link -> Intent(Intent.ACTION_VIEW, url.toUri())
    is MovieOpen.CachedFile -> cachedFileIntent(context, file)
}

private fun cachedFileIntent(context: Context, file: File): Intent {
    val appContext = context.applicationContext
    val uri = FileProvider.getUriForFile(appContext, appContext.packageName + ".provider", file)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
