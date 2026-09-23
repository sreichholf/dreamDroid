package net.reichholf.dreamdroid.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.ProfileCheckResult

private const val INTRO_HOLD_MS: Int = 700

private const val INTRO_MOVE_MS: Int = 800

@Composable
fun SetupAssistantScreen(
    viewModel: SetupAssistantViewModel,
    localNetworkGranted: Boolean,
    onRequestLocalNetwork: () -> Unit,
    onSave: (Profile) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = viewModel.draft
    val step = draft.step
    val checking = viewModel.checking
    val checkResult = viewModel.checkResult

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
    val signInReady = !draft.login || draft.user.isNotBlank()
    val signInChecked = checkResult != null && !checking
    val failed = checkResult?.hasError == true
    val actionEnabled = when (step) {
        SetupStep.Find -> draft.host.isNotBlank()

        SetupStep.Connection -> {
            val port = draft.portText.toIntOrNull()
            draft.host.isNotBlank() && port != null && port in 1..65535
        }

        SetupStep.SignIn -> signInReady && (signInChecked || !checking)

        SetupStep.Name -> !checking

        SetupStep.Welcome -> false
    }
    val actionLabel = when (step) {
        SetupStep.Name -> R.string.save

        SetupStep.SignIn -> {
            if (signInChecked) R.string.setup_next else R.string.setup_check_connection
        }

        else -> R.string.setup_next
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.displayCutout)
                    .union(WindowInsets.navigationBars)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val room = (maxHeight - 28.dp - 160.dp).coerceAtLeast(0.dp)
            val logoHeight = 320.dp.coerceAtMost(room).coerceAtLeast(160.dp.coerceAtMost(room))
            Column(
                modifier = Modifier.fillMaxSize(),
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
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
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
                                    Button(
                                        onClick = { viewModel.advance() },
                                        enabled = progress >= 1f,
                                        modifier = Modifier
                                            .focusRequester(startFocus)
                                            .graphicsLayer { alpha = progress }
                                    ) {
                                        Text(stringResource(R.string.setup_start))
                                    }
                                }

                                SetupStep.Find -> FindStep(
                                    host = draft.host,
                                    onHostChange = viewModel::onFindHostChange,
                                    devices = viewModel.devices,
                                    searching = viewModel.searching,
                                    searched = viewModel.searched,
                                    localNetworkGranted = localNetworkGranted,
                                    portText = draft.portText,
                                    onPick = viewModel::onPick
                                )

                                SetupStep.Connection -> ConnectionStep(
                                    host = draft.host,
                                    onHostChange = viewModel::onHostChange,
                                    useHttps = draft.useHttps,
                                    onHttpsChange = viewModel::onHttpsChange,
                                    portText = draft.portText,
                                    onPortChange = viewModel::onPortChange
                                )

                                SetupStep.SignIn -> SignInStep(
                                    login = draft.login,
                                    onLoginChange = viewModel::onLoginChange,
                                    user = draft.user,
                                    onUserChange = viewModel::onUserChange,
                                    pass = draft.pass,
                                    onPassChange = viewModel::onPassChange,
                                    checking = checking,
                                    result = checkResult,
                                    trustAllCerts = draft.trustAllCerts,
                                    onTrustAllChange = viewModel::onTrustAllChange,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                SetupStep.Name -> NameStep(
                                    profileName = draft.profileName,
                                    onNameChange = viewModel::onNameChange
                                )
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
                TextButton(onClick = { viewModel.back() }) {
                    Text(stringResource(R.string.setup_back))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step == SetupStep.SignIn && failed && !checking) {
                        TextButton(onClick = viewModel::check) {
                            Text(stringResource(R.string.setup_retry))
                        }
                    }
                    Button(
                        onClick = { viewModel.advance()?.let(onSave) },
                        enabled = actionEnabled
                    ) {
                        Text(stringResource(actionLabel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupTitle(step: SetupStep, progress: Float) {
    val title = when (step) {
        SetupStep.Welcome -> stringResource(R.string.setup_welcome)
        SetupStep.Find -> stringResource(R.string.setup_find_title)
        SetupStep.Connection -> stringResource(R.string.setup_connection_title)
        SetupStep.SignIn -> stringResource(R.string.setup_sign_in_title)
        SetupStep.Name -> stringResource(R.string.setup_name_title)
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
    host: String,
    onHostChange: (String) -> Unit,
    devices: List<SetupReceiver>,
    searching: Boolean,
    searched: Boolean,
    localNetworkGranted: Boolean,
    portText: String,
    onPick: (SetupReceiver) -> Unit
) {
    val addressFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { addressFocus.requestFocus() }
    }
    Text(
        text = stringResource(R.string.setup_find_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        label = { Text(stringResource(R.string.setup_address)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(addressFocus)
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
    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
        items(devices, key = { "${it.host}:${it.port}" }) { receiver ->
            val selected = host == receiver.host && portText == receiver.port.toString()
            ListItem(
                headlineContent = { Text(receiver.name) },
                supportingContent = { Text(receiver.host) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                    .clickable { onPick(receiver) }
            )
        }
    }
}

@Composable
private fun ConnectionStep(
    host: String,
    onHostChange: (String) -> Unit,
    useHttps: Boolean,
    onHttpsChange: (Boolean) -> Unit,
    portText: String,
    onPortChange: (String) -> Unit
) {
    val hostFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { hostFocus.requestFocus() }
    }
    Text(
        text = stringResource(R.string.setup_connection_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        label = { Text(stringResource(R.string.host_long)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(hostFocus)
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
        value = portText,
        onValueChange = onPortChange,
        label = { Text(stringResource(R.string.port)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_port")
    )
}

@Composable
private fun SignInStep(
    login: Boolean,
    onLoginChange: (Boolean) -> Unit,
    user: String,
    onUserChange: (String) -> Unit,
    pass: String,
    onPassChange: (String) -> Unit,
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
                value = user,
                onValueChange = onUserChange,
                label = { Text(stringResource(R.string.user)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = onPassChange,
                label = { Text(stringResource(R.string.pass)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
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
private fun NameStep(profileName: String, onNameChange: (String) -> Unit) {
    Text(
        text = stringResource(R.string.setup_name_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = profileName,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.profile_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ConnectionCheck(
    checking: Boolean,
    result: ProfileCheckResult?,
    trustAllCerts: Boolean,
    onTrustAllChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
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
            text = outcome.setupMessage(context),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
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
