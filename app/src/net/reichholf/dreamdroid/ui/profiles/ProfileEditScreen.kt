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
    form: ProfileForm,
    hostError: String?,
    onFormChange: (ProfileForm) -> Unit,
    saveLabel: String,
    onSave: () -> Unit,
    showSaveFab: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showTrustAllCertsWarning by remember { mutableStateOf(false) }
    // Hosted under the XML app bar; default Scaffold safeDrawing would double-pad
    // and lift the FAB (#263). Bottom inset is PhoneNavHost when the shell
    // destination bar is hidden. Phone ProfileEditDestination passes
    // showSaveFab=false; Save (and Delete when editing) are shell top-bar
    // actions ([saveAndDeleteActions]). The TV profiles destination
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
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = stringResource(R.string.profile_name)
                )
                EditSwitchRow(
                    checked = form.simpleRemote,
                    onCheckedChange = { onFormChange(form.copy(simpleRemote = it)) },
                    label = stringResource(R.string.simple_remote)
                )
            }

            EditFormSection(title = stringResource(R.string.connection)) {
                EditPairedRow {
                    EditOutlinedTextField(
                        value = form.host,
                        onValueChange = { onFormChange(form.copy(host = it)) },
                        label = stringResource(R.string.host_long),
                        isError = hostError != null,
                        supportingText = hostError,
                        modifier = Modifier.weight(1f)
                    )
                    EditOutlinedTextField(
                        value = form.port,
                        onValueChange = { onFormChange(form.copy(port = it)) },
                        label = stringResource(R.string.port),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(0.45f)
                    )
                }
                EditSwitchRow(
                    checked = form.ssl,
                    onCheckedChange = { onFormChange(form.withSsl(it)) },
                    label = stringResource(R.string.ssl_enabled)
                )
                if (form.ssl) {
                    EditSwitchRow(
                        checked = form.trustAllCerts,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showTrustAllCertsWarning = true
                            } else {
                                onFormChange(form.copy(trustAllCerts = false))
                            }
                        },
                        label = stringResource(R.string.trust_all_certs)
                    )
                }
                EditSwitchRow(
                    checked = form.login,
                    onCheckedChange = { onFormChange(form.copy(login = it)) },
                    label = stringResource(R.string.login_enabled)
                )
                if (form.login) {
                    EditPairedRow {
                        EditOutlinedTextField(
                            value = form.user,
                            onValueChange = { onFormChange(form.copy(user = it)) },
                            label = stringResource(R.string.user),
                            modifier = Modifier.weight(1f)
                        )
                        EditOutlinedTextField(
                            value = form.pass,
                            onValueChange = { onFormChange(form.copy(pass = it)) },
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
                    value = form.ssid,
                    onValueChange = { onFormChange(form.copy(ssid = it)) },
                    label = stringResource(R.string.ssid)
                )
                EditSwitchRow(
                    checked = form.defaultOnNoWifi,
                    onCheckedChange = { onFormChange(form.copy(defaultOnNoWifi = it)) },
                    label = stringResource(R.string.defaultOnNoWifi)
                )
            }

            EditFormSection(title = stringResource(R.string.streaming)) {
                EditOutlinedTextField(
                    value = form.streamHost,
                    onValueChange = { onFormChange(form.copy(streamHost = it)) },
                    label = stringResource(R.string.stream_host_long)
                )
                EditSwitchRow(
                    checked = form.zapAndStream,
                    onCheckedChange = { onFormChange(form.copy(zapAndStream = it)) },
                    label = stringResource(R.string.zap_and_stream),
                    summary = stringResource(R.string.zap_and_stream_summary)
                )
                EditSwitchRow(
                    checked = form.encoderStream,
                    onCheckedChange = { onFormChange(form.copy(encoderStream = it)) },
                    label = stringResource(R.string.use_encoder)
                )
                if (form.encoderStream) {
                    EncoderSection(form, onFormChange)
                } else {
                    StreamPortsSection(form, onFormChange)
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
            onConfirm = { onFormChange(form.copy(trustAllCerts = true)) },
            confirmLabel = stringResource(R.string.enable)
        )
    }
}

@Composable
private fun EncoderSection(form: ProfileForm, onFormChange: (ProfileForm) -> Unit) {
    EditPairedRow {
        EditOutlinedTextField(
            value = form.encoderPath,
            onValueChange = { onFormChange(form.copy(encoderPath = it)) },
            label = stringResource(R.string.encoder_path),
            modifier = Modifier.weight(1f)
        )
        EditOutlinedTextField(
            value = form.encoderPort,
            onValueChange = { onFormChange(form.copy(encoderPort = it)) },
            label = stringResource(R.string.encoder_port),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
    }
    EditSwitchRow(
        checked = form.encoderLogin,
        onCheckedChange = { onFormChange(form.copy(encoderLogin = it)) },
        label = stringResource(R.string.login_enabled)
    )
    if (form.encoderLogin) {
        EditPairedRow {
            EditOutlinedTextField(
                value = form.encoderUser,
                onValueChange = { onFormChange(form.copy(encoderUser = it)) },
                label = stringResource(R.string.encoder_user),
                modifier = Modifier.weight(1f)
            )
            EditOutlinedTextField(
                value = form.encoderPass,
                onValueChange = { onFormChange(form.copy(encoderPass = it)) },
                label = stringResource(R.string.encoder_pass),
                keyboardType = KeyboardType.Password,
                password = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
    EditPairedRow {
        EditOutlinedTextField(
            value = form.encoderVideoBitrate,
            onValueChange = { onFormChange(form.copy(encoderVideoBitrate = it)) },
            label = stringResource(R.string.video_bitrate),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        EditOutlinedTextField(
            value = form.encoderAudioBitrate,
            onValueChange = { onFormChange(form.copy(encoderAudioBitrate = it)) },
            label = stringResource(R.string.audio_bitrate),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StreamPortsSection(form: ProfileForm, onFormChange: (ProfileForm) -> Unit) {
    EditFormSubsection(title = stringResource(R.string.live)) {
        EditOutlinedTextField(
            value = form.streamPort,
            onValueChange = { onFormChange(form.copy(streamPort = it)) },
            label = stringResource(R.string.port_stream_live),
            keyboardType = KeyboardType.Number
        )
        EditSwitchRow(
            checked = form.streamLogin,
            onCheckedChange = { onFormChange(form.copy(streamLogin = it)) },
            label = stringResource(R.string.login)
        )
    }
    EditFormSubsection(title = stringResource(R.string.movies)) {
        EditOutlinedTextField(
            value = form.filePort,
            onValueChange = { onFormChange(form.copy(filePort = it)) },
            label = stringResource(R.string.port_stream_file),
            keyboardType = KeyboardType.Number
        )
        EditSwitchRow(
            checked = form.fileLogin,
            onCheckedChange = { onFormChange(form.copy(fileLogin = it)) },
            label = stringResource(R.string.login)
        )
        EditSwitchRow(
            checked = form.fileSsl,
            onCheckedChange = { onFormChange(form.copy(fileSsl = it)) },
            label = stringResource(R.string.ssl_enabled)
        )
    }
}
