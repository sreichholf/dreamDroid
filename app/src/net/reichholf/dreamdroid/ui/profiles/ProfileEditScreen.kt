package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.EditFormColumn
import net.reichholf.dreamdroid.ui.compose.EditFormSection
import net.reichholf.dreamdroid.ui.compose.EditFormSubsection
import net.reichholf.dreamdroid.ui.compose.EditOutlinedTextField
import net.reichholf.dreamdroid.ui.compose.EditPairedRow
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog

@Composable
fun ProfileEditScreen(
    state: ProfileEditState,
    saveLabel: String,
    onSave: () -> Unit,
    showSaveFab: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showTrustAllCertsWarning by remember { mutableStateOf(false) }
    // Hosted under the XML app bar; default Scaffold safeDrawing would double-pad
    // and lift the FAB (#263). Bottom inset is PhoneNavHost when the shell
    // destination bar is hidden. Phone ProfileEditDestination passes
    // showSaveFab=false; Save (and Delete when editing) are toolbar
    // [R.menu.save] / [R.menu.edit_delete]. The TV profiles destination
    // keeps the default in-content FAB.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (showSaveFab) {
                FloatingActionButton(onClick = onSave) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_save),
                        contentDescription = saveLabel
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        EditFormColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            EditFormSection(title = stringResource(R.string.profile)) {
                EditOutlinedTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    label = stringResource(R.string.profile_name)
                )
                EditSwitchRow(
                    checked = state.simpleRemote,
                    onCheckedChange = { state.simpleRemote = it },
                    label = stringResource(R.string.simple_remote)
                )
            }

            EditFormSection(title = stringResource(R.string.connection)) {
                EditPairedRow {
                    EditOutlinedTextField(
                        value = state.host,
                        onValueChange = {
                            state.host = it
                            state.hostError = null
                        },
                        label = stringResource(R.string.host_long),
                        isError = state.hostError != null,
                        supportingText = state.hostError,
                        modifier = Modifier.weight(1f)
                    )
                    EditOutlinedTextField(
                        value = state.port,
                        onValueChange = { state.port = it },
                        label = stringResource(R.string.port),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(0.45f)
                    )
                }
                EditSwitchRow(
                    checked = state.ssl,
                    onCheckedChange = { state.onSslChanged(it, keepPort = false) },
                    label = stringResource(R.string.ssl_enabled)
                )
                if (state.ssl) {
                    EditSwitchRow(
                        checked = state.trustAllCerts,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showTrustAllCertsWarning = true
                            } else {
                                state.trustAllCerts = false
                            }
                        },
                        label = stringResource(R.string.trust_all_certs)
                    )
                }
                EditSwitchRow(
                    checked = state.login,
                    onCheckedChange = { state.login = it },
                    label = stringResource(R.string.login_enabled)
                )
                if (state.login) {
                    EditPairedRow {
                        EditOutlinedTextField(
                            value = state.user,
                            onValueChange = { state.user = it },
                            label = stringResource(R.string.user),
                            modifier = Modifier.weight(1f)
                        )
                        EditOutlinedTextField(
                            value = state.pass,
                            onValueChange = { state.pass = it },
                            label = stringResource(R.string.pass),
                            keyboardType = KeyboardType.Password,
                            password = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            EditFormSection(title = stringResource(R.string.auto_switch_profile_wifi_based_long)) {
                EditOutlinedTextField(
                    value = state.ssid,
                    onValueChange = { state.ssid = it },
                    label = stringResource(R.string.ssid)
                )
                EditSwitchRow(
                    checked = state.defaultOnNoWifi,
                    onCheckedChange = { state.defaultOnNoWifi = it },
                    label = stringResource(R.string.defaultOnNoWifi)
                )
            }

            EditFormSection(title = stringResource(R.string.streaming)) {
                EditOutlinedTextField(
                    value = state.streamHost,
                    onValueChange = { state.streamHost = it },
                    label = stringResource(R.string.stream_host_long)
                )
                EditSwitchRow(
                    checked = state.zapAndStream,
                    onCheckedChange = { state.zapAndStream = it },
                    label = stringResource(R.string.zap_and_stream),
                    summary = stringResource(R.string.zap_and_stream_summary)
                )
                EditSwitchRow(
                    checked = state.encoderStream,
                    onCheckedChange = { state.encoderStream = it },
                    label = stringResource(R.string.use_encoder)
                )
                if (state.encoderStream) {
                    EncoderSection(state)
                } else {
                    StreamPortsSection(state)
                }
            }

            if (showSaveFab) {
                Spacer(Modifier.height(72.dp))
            }
        }
    }
    if (showTrustAllCertsWarning) {
        ConfirmAlertDialog(
            title = stringResource(R.string.trust_all_certs_confirm_title),
            message = stringResource(R.string.trust_all_certs_confirm),
            onDismiss = { showTrustAllCertsWarning = false },
            onConfirm = { state.trustAllCerts = true },
            confirmLabel = stringResource(R.string.enable)
        )
    }
}

@Composable
private fun EncoderSection(state: ProfileEditState) {
    EditPairedRow {
        EditOutlinedTextField(
            value = state.encoderPath,
            onValueChange = { state.encoderPath = it },
            label = stringResource(R.string.encoder_path),
            modifier = Modifier.weight(1f)
        )
        EditOutlinedTextField(
            value = state.encoderPort,
            onValueChange = { state.encoderPort = it },
            label = stringResource(R.string.encoder_port),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
    }
    EditSwitchRow(
        checked = state.encoderLogin,
        onCheckedChange = { state.encoderLogin = it },
        label = stringResource(R.string.login_enabled)
    )
    if (state.encoderLogin) {
        EditPairedRow {
            EditOutlinedTextField(
                value = state.encoderUser,
                onValueChange = { state.encoderUser = it },
                label = stringResource(R.string.encoder_user),
                modifier = Modifier.weight(1f)
            )
            EditOutlinedTextField(
                value = state.encoderPass,
                onValueChange = { state.encoderPass = it },
                label = stringResource(R.string.encoder_pass),
                keyboardType = KeyboardType.Password,
                password = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
    EditPairedRow {
        EditOutlinedTextField(
            value = state.encoderVideoBitrate,
            onValueChange = { state.encoderVideoBitrate = it },
            label = stringResource(R.string.video_bitrate),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        EditOutlinedTextField(
            value = state.encoderAudioBitrate,
            onValueChange = { state.encoderAudioBitrate = it },
            label = stringResource(R.string.audio_bitrate),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StreamPortsSection(state: ProfileEditState) {
    EditFormSubsection(title = stringResource(R.string.live)) {
        EditOutlinedTextField(
            value = state.streamPort,
            onValueChange = { state.streamPort = it },
            label = stringResource(R.string.port_stream_live),
            keyboardType = KeyboardType.Number
        )
        EditSwitchRow(
            checked = state.streamLogin,
            onCheckedChange = { state.streamLogin = it },
            label = stringResource(R.string.login)
        )
    }
    EditFormSubsection(title = stringResource(R.string.movies)) {
        EditOutlinedTextField(
            value = state.filePort,
            onValueChange = { state.filePort = it },
            label = stringResource(R.string.port_stream_file),
            keyboardType = KeyboardType.Number
        )
        EditSwitchRow(
            checked = state.fileLogin,
            onCheckedChange = { state.fileLogin = it },
            label = stringResource(R.string.login)
        )
        EditSwitchRow(
            checked = state.fileSsl,
            onCheckedChange = { state.fileSsl = it },
            label = stringResource(R.string.ssl_enabled)
        )
    }
}
