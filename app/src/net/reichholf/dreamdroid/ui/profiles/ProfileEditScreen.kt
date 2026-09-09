package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

@Composable
fun ProfileEditScreen(
    state: ProfileEditState,
    saveLabel: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onSave) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_save),
                    contentDescription = saveLabel,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = stringResource(R.string.profile_name),
            )
            ProfileCheckRow(
                checked = state.simpleRemote,
                onCheckedChange = { state.simpleRemote = it },
                label = stringResource(R.string.simple_remote),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileTextField(
                    value = state.host,
                    onValueChange = { state.host = it },
                    label = stringResource(R.string.host_long),
                    modifier = Modifier.weight(1f),
                )
                ProfileTextField(
                    value = state.port,
                    onValueChange = { state.port = it },
                    label = stringResource(R.string.port),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(0.4f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ProfileCheckRow(
                    checked = state.ssl,
                    onCheckedChange = { state.onSslChanged(it, keepPort = false) },
                    label = stringResource(R.string.ssl_enabled),
                    modifier = Modifier.weight(1f),
                )
                if (state.ssl) {
                    ProfileCheckRow(
                        checked = state.trustAllCerts,
                        onCheckedChange = { state.trustAllCerts = it },
                        label = stringResource(R.string.trust_all_certs),
                        modifier = Modifier.weight(1f),
                    )
                }
                ProfileCheckRow(
                    checked = state.login,
                    onCheckedChange = { state.login = it },
                    label = stringResource(R.string.login_enabled),
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.login) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfileTextField(
                        value = state.user,
                        onValueChange = { state.user = it },
                        label = stringResource(R.string.user),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileTextField(
                        value = state.pass,
                        onValueChange = { state.pass = it },
                        label = stringResource(R.string.pass),
                        keyboardType = KeyboardType.Password,
                        password = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionHeader(stringResource(R.string.auto_switch_profile_wifi_based_long))
            ProfileTextField(
                value = state.ssid,
                onValueChange = { state.ssid = it },
                label = stringResource(R.string.ssid),
            )
            ProfileCheckRow(
                checked = state.defaultOnNoWifi,
                onCheckedChange = { state.defaultOnNoWifi = it },
                label = stringResource(R.string.defaultOnNoWifi),
            )

            SectionHeader(stringResource(R.string.streaming))
            ProfileTextField(
                value = state.streamHost,
                onValueChange = { state.streamHost = it },
                label = stringResource(R.string.stream_host_long),
            )
            ProfileCheckRow(
                checked = state.encoderStream,
                onCheckedChange = { state.encoderStream = it },
                label = stringResource(R.string.use_encoder),
            )

            if (state.encoderStream) {
                EncoderSection(state)
            } else {
                StreamPortsSection(state)
            }

            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun EncoderSection(state: ProfileEditState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileTextField(
            value = state.encoderPath,
            onValueChange = { state.encoderPath = it },
            label = stringResource(R.string.encoder_path),
            modifier = Modifier.weight(1f),
        )
        ProfileTextField(
            value = state.encoderPort,
            onValueChange = { state.encoderPort = it },
            label = stringResource(R.string.encoder_port),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
    }
    ProfileCheckRow(
        checked = state.encoderLogin,
        onCheckedChange = { state.encoderLogin = it },
        label = stringResource(R.string.login_enabled),
    )
    if (state.encoderLogin) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileTextField(
                value = state.encoderUser,
                onValueChange = { state.encoderUser = it },
                label = stringResource(R.string.encoder_user),
                modifier = Modifier.weight(1f),
            )
            ProfileTextField(
                value = state.encoderPass,
                onValueChange = { state.encoderPass = it },
                label = stringResource(R.string.encoder_pass),
                keyboardType = KeyboardType.Password,
                password = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileTextField(
            value = state.encoderVideoBitrate,
            onValueChange = { state.encoderVideoBitrate = it },
            label = stringResource(R.string.video_bitrate),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
        ProfileTextField(
            value = state.encoderAudioBitrate,
            onValueChange = { state.encoderAudioBitrate = it },
            label = stringResource(R.string.audio_bitrate),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StreamPortsSection(state: ProfileEditState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionHeader(stringResource(R.string.live))
            ProfileTextField(
                value = state.streamPort,
                onValueChange = { state.streamPort = it },
                label = stringResource(R.string.port_stream_live),
                keyboardType = KeyboardType.Number,
            )
            ProfileCheckRow(
                checked = state.streamLogin,
                onCheckedChange = { state.streamLogin = it },
                label = stringResource(R.string.login),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            SectionHeader(stringResource(R.string.movies))
            ProfileTextField(
                value = state.filePort,
                onValueChange = { state.filePort = it },
                label = stringResource(R.string.port_stream_file),
                keyboardType = KeyboardType.Number,
            )
            ProfileCheckRow(
                checked = state.fileLogin,
                onCheckedChange = { state.fileLogin = it },
                label = stringResource(R.string.login),
            )
            ProfileCheckRow(
                checked = state.fileSsl,
                onCheckedChange = { state.fileSsl = it },
                label = stringResource(R.string.ssl_enabled),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun ProfileCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
