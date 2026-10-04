package net.reichholf.dreamdroid.ui.autotimer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage

/**
 * AutoTimer create/edit. [pickedTargets] is this entry's result slot for the target picker.
 * A save that went through shows the saved AutoTimer's preview.
 */
@Composable
fun AutoTimerEditDestination(
    handle: PhoneNavHandle,
    pickedTargets: StateFlow<List<Target>?>,
    onPickedTargetsHandled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutoTimerEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val picked by pickedTargets.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    LaunchedEffect(picked, uiState.content) {
        val targets = picked
        if (targets != null && uiState.content == AutoTimerEditContent.Editing) {
            viewModel.addTargets(targets)
            onPickedTargetsHandled()
        }
    }

    LaunchedEffect(uiState.saved) {
        uiState.saved?.let { saved ->
            viewModel.onSavedHandled()
            handle.showAutoTimerAfterSave(saved.id, saved.name)
        }
    }

    BindShellTopBarActions(
        listOf(
            ShellTopBarAction(
                id = Statics.ITEM_SAVE,
                label = stringResource(R.string.save),
                enabled = uiState.editable,
                onClick = {
                    if (uiState.blocked) handle.requestNeedsReceiver() else viewModel.save()
                }
            )
        ),
        keepInView = true
    )

    AutoTimerEditScreen(
        state = uiState,
        fields = AutoTimerEditFields(
            match = viewModel.match.state,
            name = viewModel.name.state,
            filter = viewModel.filterText.state,
            offsetBefore = viewModel.offsetBefore.state,
            offsetAfter = viewModel.offsetAfter.state,
            maxDuration = viewModel.maxDuration.state
        ),
        actions = viewModel,
        onPickTargets = { handle.navigateToAutoTimerTargetPick() },
        modifier = modifier
    )
}
