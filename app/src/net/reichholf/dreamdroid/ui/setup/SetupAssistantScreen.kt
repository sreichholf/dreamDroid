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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    localNetworkGranted: Boolean,
    onRequestLocalNetwork: () -> Unit,
    onSearch: suspend () -> List<SetupReceiver>,
    onCheck: suspend (Profile) -> ProfileCheckResult,
    onSave: (Profile) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stepName by rememberSaveable { mutableStateOf(SetupStep.Welcome.name) }
    var host by rememberSaveable { mutableStateOf("") }
    var useHttps by rememberSaveable { mutableStateOf(false) }
    var portText by rememberSaveable { mutableStateOf("80") }
    var login by rememberSaveable { mutableStateOf(true) }
    var user by rememberSaveable { mutableStateOf("root") }
    var pass by rememberSaveable { mutableStateOf("dreambox") }
    var profileName by rememberSaveable { mutableStateOf("") }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var trustAllCerts by rememberSaveable { mutableStateOf(false) }
    var suggestedName by rememberSaveable { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<SetupReceiver>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var askedForNetwork by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<ProfileCheckResult?>(null) }
    var submittedCheck by remember { mutableIntStateOf(0) }
    var checkEpoch by remember { mutableIntStateOf(0) }
    val step = SetupStep.entries.firstOrNull { it.name == stepName } ?: SetupStep.Welcome

    fun portNumber(): Int = portText.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: if (useHttps) 443 else 80

    fun draft(): Profile = wizardProfile(
        name = profileName,
        host = host,
        port = portNumber(),
        useHttps = useHttps,
        login = login,
        user = user,
        pass = pass,
        trustAllCerts = trustAllCerts
    )

    fun startCheck() {
        checkEpoch += 1
        submittedCheck = checkEpoch
    }

    fun clearCheckResult() {
        checkEpoch += 1
        checkResult = null
        checking = false
    }

    BackHandler {
        when (step) {
            SetupStep.Welcome -> onLeave()
            SetupStep.Find -> stepName = SetupStep.Welcome.name
            SetupStep.Connection -> stepName = SetupStep.Find.name
            SetupStep.SignIn -> stepName = SetupStep.Connection.name
            SetupStep.Name -> stepName = SetupStep.SignIn.name
        }
    }

    LaunchedEffect(step, localNetworkGranted) {
        if (step != SetupStep.Find) {
            return@LaunchedEffect
        }
        if (!askedForNetwork) {
            askedForNetwork = true
            onRequestLocalNetwork()
        }
        if (!localNetworkGranted || searched) {
            return@LaunchedEffect
        }
        searching = true
        devices = onSearch()
        searching = false
        searched = true
    }

    LaunchedEffect(submittedCheck) {
        val serial = submittedCheck
        if (serial == 0 || serial != checkEpoch) {
            return@LaunchedEffect
        }
        checking = true
        checkResult = null
        val profile = draft()
        try {
            val result = onCheck(profile)
            if (serial == checkEpoch) {
                checkResult = result
            }
        } finally {
            if (serial == checkEpoch) {
                checking = false
            }
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
    val signInReady = !login || user.isNotBlank()
    val signInChecked = checkResult != null && !checking
    val failed = checkResult?.hasError == true
    val actionEnabled = when (step) {
        SetupStep.Find -> host.isNotBlank()

        SetupStep.Connection -> {
            val port = portText.toIntOrNull()
            host.isNotBlank() && port != null && port in 1..65535
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
                                        onClick = { stepName = SetupStep.Find.name },
                                        enabled = progress >= 1f,
                                        modifier = Modifier
                                            .focusRequester(startFocus)
                                            .graphicsLayer { alpha = progress }
                                    ) {
                                        Text(stringResource(R.string.setup_start))
                                    }
                                }

                                SetupStep.Find -> FindStep(
                                    host = host,
                                    onHostChange = {
                                        host = it
                                        suggestedName = ""
                                        clearCheckResult()
                                    },
                                    devices = devices,
                                    searching = searching,
                                    searched = searched,
                                    localNetworkGranted = localNetworkGranted,
                                    portText = portText,
                                    onPick = { receiver ->
                                        host = receiver.host
                                        portText = receiver.port.toString()
                                        useHttps = receiver.port == 443
                                        suggestedName = receiver.name
                                        nameEdited = false
                                        clearCheckResult()
                                    }
                                )

                                SetupStep.Connection -> ConnectionStep(
                                    host = host,
                                    onHostChange = {
                                        host = it
                                        clearCheckResult()
                                    },
                                    useHttps = useHttps,
                                    onHttpsChange = { https ->
                                        val current = portText.toIntOrNull()
                                        val wasDefault =
                                            current == null || current == 80 || current == 443
                                        useHttps = https
                                        if (wasDefault) {
                                            portText = if (https) {
                                                "443"
                                            } else {
                                                "80"
                                            }
                                        }
                                        clearCheckResult()
                                    },
                                    portText = portText,
                                    onPortChange = {
                                        portText = it
                                        clearCheckResult()
                                    }
                                )

                                SetupStep.SignIn -> SignInStep(
                                    login = login,
                                    onLoginChange = {
                                        login = it
                                        clearCheckResult()
                                    },
                                    user = user,
                                    onUserChange = {
                                        user = it
                                        clearCheckResult()
                                    },
                                    pass = pass,
                                    onPassChange = {
                                        pass = it
                                        clearCheckResult()
                                    },
                                    checking = checking,
                                    result = checkResult,
                                    trustAllCerts = trustAllCerts,
                                    onTrustAllChange = { enabled ->
                                        trustAllCerts = enabled
                                        startCheck()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                SetupStep.Name -> NameStep(
                                    profileName = profileName,
                                    onNameChange = {
                                        profileName = it
                                        nameEdited = true
                                    }
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
                TextButton(onClick = {
                    stepName = when (step) {
                        SetupStep.Find -> SetupStep.Welcome.name
                        SetupStep.Connection -> SetupStep.Find.name
                        SetupStep.SignIn -> SetupStep.Connection.name
                        SetupStep.Name -> SetupStep.SignIn.name
                        SetupStep.Welcome -> SetupStep.Welcome.name
                    }
                }) {
                    Text(stringResource(R.string.setup_back))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (step == SetupStep.SignIn && failed && !checking) {
                        TextButton(onClick = { startCheck() }) {
                            Text(stringResource(R.string.setup_retry))
                        }
                    }
                    Button(
                        onClick = {
                            when (step) {
                                SetupStep.Find -> stepName = SetupStep.Connection.name

                                SetupStep.Connection -> stepName = SetupStep.SignIn.name

                                SetupStep.SignIn -> {
                                    if (signInChecked) {
                                        if (!nameEdited) {
                                            profileName = suggestedName.ifBlank { host.trim() }
                                        }
                                        stepName = SetupStep.Name.name
                                    } else {
                                        startCheck()
                                    }
                                }

                                SetupStep.Name -> {
                                    if (profileName.isBlank()) {
                                        profileName = host.trim()
                                    }
                                    onSave(draft())
                                }

                                SetupStep.Welcome -> Unit
                            }
                        },
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
