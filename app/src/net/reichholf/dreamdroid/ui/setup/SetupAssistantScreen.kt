package net.reichholf.dreamdroid.ui.setup

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.ui.backup.ImportReview
import net.reichholf.dreamdroid.ui.backup.ImportReviewBody
import net.reichholf.dreamdroid.ui.text.asString
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvActiveFocusColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvFocusColors

private const val INTRO_HOLD_MS: Int = 700

private const val INTRO_MOVE_MS: Int = 800

/** The logo fills the Welcome step; later steps only keep it as a compact mark. */
private val WELCOME_LOGO_HEIGHT = 320.dp

private val STEP_LOGO_HEIGHT = 120.dp

@Composable
fun SetupAssistantScreen(
    viewModel: SetupAssistantViewModel,
    localNetworkGranted: Boolean,
    onRequestLocalNetwork: () -> Unit,
    onFinished: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val review by viewModel.importReview.collectAsStateWithLifecycle()
    val draft = state.draft
    val step = draft.step
    val checking = state.checking
    val checkResult = state.checkResult
    val restoring = state.restoring
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(state.finished) {
        if (state.finished) {
            viewModel.onFinishHandled()
            currentOnFinished()
        }
    }

    val pickRestoreFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreFrom(it.toString()) }
    }

    fun openRestorePicker() {
        try {
            pickRestoreFile.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            viewModel.onRestorePickerMissing()
        }
    }

    BackHandler {
        if (!viewModel.back()) {
            onLeave()
        }
    }

    LaunchedEffect(step, localNetworkGranted) {
        if (step != SetupStep.Find) {
            return@LaunchedEffect
        }
        if (viewModel.claimNetworkRequest()) {
            onRequestLocalNetwork()
        }
        if (localNetworkGranted) {
            viewModel.search()
        }
    }

    val intro = remember { Animatable(0f) }
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(INTRO_HOLD_MS.toLong())
        intro.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = INTRO_MOVE_MS, easing = FastOutSlowInEasing)
        )
        runCatching { startFocus.requestFocus() }
    }
    val progress = intro.value
    val hostText = viewModel.host.state.text
    val portText = viewModel.port.state.text.toString()
    val signInReady = !draft.login || viewModel.user.state.text.isNotBlank()
    val signInChecked = checkResult != null && !checking
    val failed = checkResult?.hasError == true
    // A running check or restore keeps the action enabled: a disabled button drops D-pad
    // focus onto a text field, which opens the keyboard on TV.
    val busy = (step == SetupStep.SignIn && checking) || (step == SetupStep.Import && restoring)
    val actionEnabled = when (step) {
        SetupStep.Find -> hostText.isNotBlank()

        SetupStep.Connection -> {
            val port = portText.toIntOrNull()
            hostText.isNotBlank() && port != null && port in 1..65535
        }

        SetupStep.SignIn -> signInReady

        SetupStep.Name -> !checking

        SetupStep.Import -> review?.canImport == true

        SetupStep.Welcome -> false
    }
    val actionLabel = when (step) {
        SetupStep.Name -> R.string.save

        SetupStep.SignIn -> {
            if (signInChecked) R.string.setup_next else R.string.setup_check_connection
        }

        SetupStep.Import -> R.string.setup_restore

        else -> R.string.setup_next
    }
    val scrollState = rememberScrollState()
    // Saved, so recreation (rotation) keeps the restored scroll; only a new step starts at the top.
    var scrolledStep by rememberSaveable { mutableStateOf(step) }
    LaunchedEffect(step) {
        if (step != scrolledStep) {
            scrolledStep = step
            scrollState.scrollTo(0)
        }
    }
    val remote = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val room = (maxHeight - 28.dp - 160.dp).coerceAtLeast(0.dp)
            val logoCap = if (step == SetupStep.Welcome) WELCOME_LOGO_HEIGHT else STEP_LOGO_HEIGHT
            val logoHeight = logoCap.coerceAtMost(room)
            CompositionLocalProvider(LocalWizardViewport provides maxHeight) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    Image(
                        painter = painterResource(R.drawable.dreamdroid_logo_simple),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(logoHeight)
                            .testTag("setup_logo")
                    )
                    Spacer(Modifier.height(28.dp))
                    AnimatedContent(
                        targetState = step,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(
                                y = if (step == SetupStep.Welcome) {
                                    48.dp * (1f - progress)
                                } else {
                                    0.dp
                                }
                            ),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 320)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 200))
                        },
                        label = "setup-title"
                    ) { current ->
                        SetupTitle(
                            step = current,
                            progress = if (current == SetupStep.Welcome) progress else 1f
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        AnimatedContent(
                            targetState = step,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                (
                                    fadeIn(animationSpec = tween(durationMillis = 320)) +
                                        slideInVertically(
                                            animationSpec = tween(durationMillis = 320)
                                        ) { it / 8 }
                                    ) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = 200))
                            },
                            label = "setup-body"
                        ) { current ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                when (current) {
                                    SetupStep.Welcome -> Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val startAction = rememberRemoteFocus()
                                            val restoreAction = rememberRemoteFocus()
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .graphicsLayer { alpha = progress }
                                            ) {
                                                Button(
                                                    onClick = viewModel::advance,
                                                    enabled = progress >= 1f,
                                                    colors = startAction.buttonColors(
                                                        ButtonDefaults.buttonColors()
                                                    ),
                                                    modifier = startAction.modifier
                                                        .focusRequester(startFocus)
                                                ) {
                                                    Text(stringResource(R.string.setup_start))
                                                }
                                                TextButton(
                                                    onClick = { openRestorePicker() },
                                                    enabled = progress >= 1f,
                                                    colors = restoreAction.buttonColors(
                                                        ButtonDefaults.textButtonColors()
                                                    ),
                                                    modifier = restoreAction.modifier
                                                        .testTag("setup_restore")
                                                ) {
                                                    if (restoring) {
                                                        CircularProgressIndicator(
                                                            strokeWidth = 2.dp,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    Text(stringResource(R.string.setup_restore))
                                                }
                                            }
                                            state.restoreError?.let { error ->
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    text = error.asString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier
                                                        .testTag("setup_restore_error")
                                                        .semantics {
                                                            liveRegion = LiveRegionMode.Polite
                                                        }
                                                )
                                            }
                                        }
                                    }

                                    SetupStep.Find -> FindStep(
                                        host = viewModel.host.state,
                                        devices = state.devices,
                                        searching = state.searching,
                                        searched = state.searched,
                                        localNetworkGranted = localNetworkGranted,
                                        portText = portText,
                                        remote = remote,
                                        onPick = viewModel::onPick
                                    )

                                    SetupStep.Connection -> ConnectionStep(
                                        host = viewModel.host.state,
                                        useHttps = draft.useHttps,
                                        onHttpsChange = viewModel::onHttpsChange,
                                        port = viewModel.port.state
                                    )

                                    SetupStep.SignIn -> SignInStep(
                                        login = draft.login,
                                        onLoginChange = viewModel::onLoginChange,
                                        user = viewModel.user.state,
                                        pass = viewModel.pass.state,
                                        checking = checking,
                                        result = checkResult,
                                        trustAllCerts = draft.trustAllCerts,
                                        onTrustAllChange = viewModel::onTrustAllChange,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    SetupStep.Name -> NameStep(
                                        profileName = viewModel.profileName.state
                                    )

                                    SetupStep.Import -> RestoreStep(
                                        review = review,
                                        onProfileCheckedChange = viewModel::setImportProfileChecked,
                                        onAllProfilesCheckedChange =
                                            viewModel::setAllImportProfilesChecked,
                                        onPasswordsChange = viewModel::setImportPasswords,
                                        onSettingsChange = viewModel::setImportSettings
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (step != SetupStep.Welcome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backFocus = rememberRemoteFocus()
                TextButton(
                    onClick = { viewModel.back() },
                    colors = backFocus.buttonColors(ButtonDefaults.textButtonColors()),
                    modifier = backFocus.modifier
                ) {
                    Text(stringResource(R.string.setup_back))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step == SetupStep.SignIn && failed && !checking) {
                        val retryFocus = rememberRemoteFocus()
                        TextButton(
                            onClick = viewModel::check,
                            colors = retryFocus.buttonColors(ButtonDefaults.textButtonColors()),
                            modifier = retryFocus.modifier
                        ) {
                            Text(stringResource(R.string.setup_retry))
                        }
                    }
                    val checkingText = stringResource(R.string.checking)
                    val actionFocus = rememberRemoteFocus()
                    Button(
                        onClick = viewModel::advance,
                        enabled = actionEnabled,
                        colors = actionFocus.buttonColors(ButtonDefaults.buttonColors()),
                        modifier = actionFocus.modifier.semantics {
                            if (step == SetupStep.SignIn && checking) {
                                stateDescription = checkingText
                            }
                        }
                    ) {
                        if (busy) {
                            // The only spinner otherwise sits in the scrolling body, below
                            // the fold on TV; keep progress on the action the remote holds.
                            CircularProgressIndicator(
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("setup_checking")
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(actionLabel))
                    }
                }
            }
        }
    }
}

/**
 * The TV hub's focus look ([dreamDroidTvFocusColors], focused scale 1.05) for the wizard's
 * actions. Only while a remote or keyboard drives the UI: touch mode keeps the phone look.
 * [modifier] must come first in the chain, above the component's own focus target.
 */
private class RemoteFocus(val modifier: Modifier, val highlighted: Boolean) {
    @Composable
    fun buttonColors(default: ButtonColors): ButtonColors = if (highlighted) {
        val focus = dreamDroidTvFocusColors()
        default.copy(containerColor = focus.container, contentColor = focus.content)
    } else {
        default
    }
}

@Composable
private fun rememberRemoteFocus(): RemoteFocus {
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    var focused by remember { mutableStateOf(false) }
    val highlighted = keyboard && focused
    val scale by animateFloatAsState(if (highlighted) 1.05f else 1f, label = "remote-focus")
    return RemoteFocus(
        modifier = Modifier
            .zIndex(if (highlighted) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        highlighted = highlighted
    )
}

/** Height of the wizard's scrolling area; it shrinks when the on-screen keyboard opens. */
private val LocalWizardViewport = compositionLocalOf { 0.dp }

/**
 * Scrolls a focused field back into view when the scrolling area gets shorter. The scroll
 * container does not do this when the keyboard opens (seen on an API 26 TV); only typing would
 * scroll to the cursor.
 */
@Composable
private fun Modifier.keepInViewWhenFocused(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val viewport = LocalWizardViewport.current
    LaunchedEffect(focused, viewport) {
        if (focused) {
            // The logo resizes with the window in the same pass; scroll once it is placed.
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return bringIntoViewRequester(requester).onFocusChanged { focused = it.hasFocus }
}

@Composable
private fun SetupTitle(step: SetupStep, progress: Float) {
    val title = when (step) {
        SetupStep.Welcome -> stringResource(R.string.setup_welcome)
        SetupStep.Find -> stringResource(R.string.setup_find_title)
        SetupStep.Connection -> stringResource(R.string.setup_connection_title)
        SetupStep.SignIn -> stringResource(R.string.setup_sign_in_title)
        SetupStep.Name -> stringResource(R.string.setup_name_title)
        SetupStep.Import -> stringResource(R.string.setup_restore_title)
    }
    val fontSize = if (step == SetupStep.Welcome) {
        (48f * (1f - progress) + 28f * progress).sp
    } else {
        MaterialTheme.typography.headlineSmall.fontSize
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = fontSize),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )
}

@Composable
private fun FindStep(
    host: TextFieldState,
    devices: List<SetupReceiver>,
    searching: Boolean,
    searched: Boolean,
    localNetworkGranted: Boolean,
    portText: String,
    remote: Boolean,
    onPick: (SetupReceiver) -> Unit
) {
    Text(
        text = stringResource(R.string.setup_find_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        state = host,
        label = { Text(stringResource(R.string.setup_address)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .keepInViewWhenFocused()
            .testTag("setup_address")
    )
    Spacer(Modifier.height(12.dp))
    if (!localNetworkGranted) {
        Text(
            text = stringResource(R.string.setup_lan_denied),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
    }
    val awaitingResults = searching || (localNetworkGranted && !searched)
    if (awaitingResults) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.setup_searching),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
    } else if (localNetworkGranted && devices.isEmpty()) {
        Text(
            text = stringResource(R.string.setup_none_found),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
    }
    val firstResult = remember { FocusRequester() }
    // Once per visit, unless an address is being typed: a remote starts on the first result, not
    // on the address field above it, whose focus opens the keyboard. Where focus went when Start
    // left the screen does not matter.
    var firstResultClaimed by remember { mutableStateOf(false) }
    var addressEdited by remember { mutableStateOf(false) }
    LaunchedEffect(host) {
        snapshotFlow { host.text.toString() }.drop(1).collect { addressEdited = true }
    }
    LazyColumn(
        modifier = Modifier
            .heightIn(max = 240.dp)
            .selectableGroup()
    ) {
        itemsIndexed(devices, key = { _, it -> "${it.host}:${it.port}" }) { index, receiver ->
            val selected =
                host.text.toString() == receiver.host && portText == receiver.port.toString()
            if (index == 0 && remote && !firstResultClaimed && !addressEdited) {
                LaunchedEffect(Unit) {
                    runCatching { firstResult.requestFocus() }
                    firstResultClaimed = true
                }
            }
            val focus = rememberRemoteFocus()
            val scheme = MaterialTheme.colorScheme
            ListItem(
                headlineContent = { Text(receiver.name) },
                supportingContent = { Text(receiver.host) },
                // The container is the ListItem's own: a background modifier would sit under it.
                colors = when {
                    focus.highlighted -> {
                        val colors = if (selected) {
                            dreamDroidTvActiveFocusColors()
                        } else {
                            dreamDroidTvFocusColors()
                        }
                        ListItemDefaults.colors(
                            containerColor = colors.container,
                            headlineColor = colors.content,
                            supportingColor = colors.content
                        )
                    }

                    selected -> ListItemDefaults.colors(
                        containerColor = scheme.secondaryContainer,
                        headlineColor = scheme.onSecondaryContainer,
                        supportingColor = scheme.onSecondaryContainer
                    )

                    else -> ListItemDefaults.colors()
                },
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstResult) else Modifier)
                    .then(focus.modifier)
                    .fillMaxWidth()
                    .testTag("setup_device_${receiver.host}")
                    .selectable(selected = selected, role = Role.RadioButton) {
                        onPick(receiver)
                    }
            )
        }
    }
}

@Composable
private fun ConnectionStep(
    host: TextFieldState,
    useHttps: Boolean,
    onHttpsChange: (Boolean) -> Unit,
    port: TextFieldState
) {
    Text(
        text = stringResource(R.string.setup_connection_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        state = host,
        label = { Text(stringResource(R.string.host_long)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .keepInViewWhenFocused()
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !useHttps,
            onClick = { onHttpsChange(false) },
            label = { Text(stringResource(R.string.setup_http)) }
        )
        FilterChip(
            selected = useHttps,
            onClick = { onHttpsChange(true) },
            label = { Text(stringResource(R.string.setup_https)) },
            modifier = Modifier.testTag("setup_https")
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        state = port,
        label = { Text(stringResource(R.string.port)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .keepInViewWhenFocused()
            .testTag("setup_port")
    )
}

@Composable
private fun SignInStep(
    login: Boolean,
    onLoginChange: (Boolean) -> Unit,
    user: TextFieldState,
    pass: TextFieldState,
    checking: Boolean,
    result: ProfileCheckResult?,
    trustAllCerts: Boolean,
    onTrustAllChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.setup_sign_in_body),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.login_enabled),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(checked = login, onCheckedChange = onLoginChange)
        }
        if (login) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                state = user,
                label = { Text(stringResource(R.string.user)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .keepInViewWhenFocused()
                    .testTag("setup_user")
            )
            Spacer(Modifier.height(12.dp))
            OutlinedSecureTextField(
                state = pass,
                label = { Text(stringResource(R.string.pass)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .keepInViewWhenFocused()
                    .testTag("setup_password")
            )
        }
        if (checking || result != null) {
            Spacer(Modifier.height(20.dp))
            ConnectionCheck(
                checking = checking,
                result = result,
                trustAllCerts = trustAllCerts,
                onTrustAllChange = onTrustAllChange
            )
        }
    }
}

@Composable
private fun NameStep(profileName: TextFieldState) {
    Text(
        text = stringResource(R.string.setup_name_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        state = profileName,
        label = { Text(stringResource(R.string.profile_name)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier
            .fillMaxWidth()
            .keepInViewWhenFocused()
    )
}

@Composable
private fun RestoreStep(
    review: ImportReview?,
    onProfileCheckedChange: (index: Int, checked: Boolean) -> Unit,
    onAllProfilesCheckedChange: (Boolean) -> Unit,
    onPasswordsChange: (Boolean) -> Unit,
    onSettingsChange: (Boolean) -> Unit
) {
    Text(
        text = stringResource(R.string.setup_restore_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    if (review == null) {
        return
    }
    // The step body scrolls as a whole, so the profile list must not scroll inside it.
    ImportReviewBody(
        review = review,
        onProfileCheckedChange = onProfileCheckedChange,
        onAllProfilesCheckedChange = onAllProfilesCheckedChange,
        onPasswordsChange = onPasswordsChange,
        onSettingsChange = onSettingsChange,
        scrollProfiles = false
    )
}

@Composable
private fun ConnectionCheck(
    checking: Boolean,
    result: ProfileCheckResult?,
    trustAllCerts: Boolean,
    onTrustAllChange: (Boolean) -> Unit
) {
    // A running or failed check is the moment the user must read the block; on a short TV
    // window it otherwise lies below the fold. The first frame after composition has not
    // placed it yet.
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(checking, result) {
        if (checking || result?.hasError == true) {
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
    ) {
        Text(
            text = stringResource(R.string.setup_test_body),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        val outcome = result
        if (checking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.checking),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        } else if (outcome != null && !outcome.hasError) {
            Text(
                text = stringResource(R.string.setup_connected),
                style = MaterialTheme.typography.bodyLarge
            )
        } else if (outcome != null && outcome.hasError) {
            Text(
                text = outcome.setupMessage()?.asString().orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (trustAllCerts || outcome?.isCertificateFailure() == true) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.trust_all_certs),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = trustAllCerts,
                    onCheckedChange = onTrustAllChange,
                    modifier = Modifier.testTag("setup_trust_all")
                )
            }
            Text(
                text = stringResource(R.string.trust_all_certs_confirm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
