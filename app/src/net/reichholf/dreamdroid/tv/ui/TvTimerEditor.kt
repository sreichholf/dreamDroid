package net.reichholf.dreamdroid.tv.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.timers.TimerEditContent
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import net.reichholf.dreamdroid.ui.timers.TimerFormActions

/**
 * TV / overlay timer create-edit. The working copy lives on [TvTimerEditViewModel], so
 * it survives a configuration change and the in-host service pick. Back dismisses.
 */
@Composable
fun TvTimerEditorHost(
    timer: Timer,
    isCreate: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvTimerEditViewModel = hiltViewModel()
) {
    val activity = LocalActivity.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)

    DisposableEffect(timer, isCreate, viewModel) {
        viewModel.bind(timer, isCreate)
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                viewModel.release(timer, isCreate)
            }
        }
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            viewModel.onFinishHandled()
            currentOnSaved()
        }
    }

    TvTimerEditorContent(
        uiState = uiState,
        name = viewModel.name.state,
        description = viewModel.description.state,
        actions = viewModel,
        servicePickKey = viewModel.servicePickKey(),
        onServicePicked = viewModel::onServicePicked,
        onSave = viewModel::save,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * The editor of [uiState] with the in-host service pick, whose ViewModel is
 * [servicePickKey]. While
 * [TimerEditUiState.mutationsBlocked], save shows [TvNeedsReceiverOverlay] instead.
 */
@Composable
fun TvTimerEditorContent(
    uiState: TimerEditUiState,
    name: TextFieldState,
    description: TextFieldState,
    actions: TimerFormActions,
    servicePickKey: String,
    onServicePicked: (Service) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingService by remember { mutableStateOf(false) }
    var showNeedsReceiver by remember { mutableStateOf(false) }
    val initialFocus = remember { FocusRequester() }
    val serviceFocus = remember { FocusRequester() }
    var returnedFromPicker by remember { mutableStateOf(false) }
    val formShown = !pickingService && uiState.timer != null

    BackHandler(enabled = !pickingService, onBack = onDismiss)

    // The form takes focus when it appears, so focus does not fall back to the hub drawer
    // when the opening button leaves the composition. Back from the service picker, focus
    // returns to the service field.
    LaunchedEffect(formShown) {
        if (formShown) {
            if (returnedFromPicker) serviceFocus.requestFocus() else initialFocus.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize().focusGroup()) {
        if (pickingService) {
            TvTimerServicePick(
                viewModelKey = servicePickKey,
                onPicked = { service ->
                    onServicePicked(service)
                    returnedFromPicker = true
                    pickingService = false
                },
                onDismiss = {
                    returnedFromPicker = true
                    pickingService = false
                }
            )
        } else {
            TimerEditContent(
                uiState = uiState,
                name = name,
                description = description,
                actions = actions,
                onPickService = { pickingService = true },
                showSaveFab = true,
                initialFocusRequester = initialFocus,
                serviceFocusRequester = serviceFocus,
                onSave = {
                    if (uiState.mutationsBlocked) {
                        showNeedsReceiver = true
                    } else {
                        onSave()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showNeedsReceiver) {
            TvNeedsReceiverOverlay(onDismiss = { showNeedsReceiver = false })
        }
    }
}
