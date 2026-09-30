package net.reichholf.dreamdroid.ui.autotimer

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction

/**
 * Bouquets and channels for the AutoTimer editor. Add hands the picks back to the editor;
 * back on a bouquet's channels returns to the bouquet list.
 */
@Composable
fun AutoTimerTargetPickDestination(
    handle: PhoneNavHandle,
    modifier: Modifier = Modifier,
    viewModel: AutoTimerTargetPickViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)

    BackHandler(enabled = uiState.bouquet != null) {
        viewModel.showBouquets()
    }

    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = Statics.ITEM_SAVE,
                label = stringResource(R.string.autotimer_add_targets, uiState.selected.size),
                enabled = uiState.selected.isNotEmpty(),
                onClick = { handle.deliverAutoTimerTargets(uiState.selected) }
            )
        )
    )

    AutoTimerTargetPickScreen(
        state = uiState,
        onRefresh = viewModel::reload,
        onOpen = viewModel::open,
        onToggle = viewModel::toggle,
        modifier = modifier
    )
}
