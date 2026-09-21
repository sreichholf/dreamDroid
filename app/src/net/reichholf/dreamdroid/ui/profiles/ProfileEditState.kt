package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class ProfileEditState {
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var hostError by mutableStateOf<String?>(null)
    var streamHost by mutableStateOf("")
    var port by mutableStateOf("80")
    var streamPort by mutableStateOf("8001")
    var filePort by mutableStateOf("80")
    var ssl by mutableStateOf(false)
    var trustAllCerts by mutableStateOf(false)
    var login by mutableStateOf(false)
    var streamLogin by mutableStateOf(false)
    var fileSsl by mutableStateOf(false)
    var fileLogin by mutableStateOf(false)
    var user by mutableStateOf("")
    var pass by mutableStateOf("")
    var simpleRemote by mutableStateOf(false)
    var ssid by mutableStateOf("")
    var defaultOnNoWifi by mutableStateOf(false)
    var encoderStream by mutableStateOf(false)
    var zapAndStream by mutableStateOf(false)
    var encoderLogin by mutableStateOf(false)
    var encoderPath by mutableStateOf("stream")
    var encoderUser by mutableStateOf("")
    var encoderPass by mutableStateOf("")
    var encoderVideoBitrate by mutableStateOf("2500")
    var encoderAudioBitrate by mutableStateOf("128")
    var encoderPort by mutableStateOf("554")

    fun loadFrom(profile: Profile) {
        name = profile.name.orEmpty()
        host = profile.host.orEmpty()
        hostError = null
        streamHost = profile.streamHost.orEmpty()
        ssl = profile.ssl
        trustAllCerts = profile.allCertsTrusted
        port = profile.port.toString()
        streamPort = profile.streamPort.toString()
        filePort = profile.filePort.toString()
        login = profile.login
        streamLogin = profile.streamLogin
        user = profile.user.orEmpty()
        pass = profile.pass.orEmpty()
        fileLogin = profile.fileLogin
        fileSsl = profile.fileSsl
        simpleRemote = profile.simpleRemote
        ssid = profile.ssid.orEmpty()
        defaultOnNoWifi = profile.isDefaultProfileOnNoWifi
        encoderStream = profile.encoderStream
        zapAndStream = profile.zapAndStream
        encoderPath = profile.encoderPath.orEmpty()
        encoderPort = profile.encoderPort.toString()
        encoderLogin = profile.encoderLogin
        encoderUser = profile.encoderUser.orEmpty()
        encoderPass = profile.encoderPass.orEmpty()
        encoderVideoBitrate = profile.encoderVideoBitrate.toString()
        encoderAudioBitrate = profile.encoderAudioBitrate.toString()
    }

    fun applyTo(profile: Profile) {
        profile.name = name
        profile.host = host.trim()
        profile.streamHost = streamHost.trim()
        profile.setPort(port, ssl, trustAllCerts)
        profile.setStreamPort(streamPort)
        profile.setFilePort(filePort)
        profile.login = login
        profile.streamLogin = streamLogin
        profile.fileLogin = fileLogin
        profile.fileSsl = fileSsl
        profile.user = user
        profile.pass = pass
        profile.simpleRemote = simpleRemote
        profile.ssid = ssid.trim()
        profile.isDefaultProfileOnNoWifi = defaultOnNoWifi
        profile.encoderStream = encoderStream
        profile.zapAndStream = zapAndStream
        profile.encoderPath = encoderPath
        profile.setEncoderPort(encoderPort)
        profile.encoderLogin = encoderLogin
        profile.encoderUser = encoderUser
        profile.encoderPass = encoderPass
        profile.setEncoderAudioBitrate(encoderAudioBitrate)
        profile.setEncoderVideoBitrate(encoderVideoBitrate)
    }

    fun onSslChanged(checked: Boolean, keepPort: Boolean) {
        ssl = checked
        if (!keepPort) {
            port = if (checked) "443" else "80"
        }
    }

    companion object {
        fun fromProfile(profile: Profile): ProfileEditState =
            ProfileEditState().also { it.loadFrom(profile) }
    }
}

fun ComposeView.bindProfileEditScreen(
    state: ProfileEditState,
    saveLabel: String,
    onSave: () -> Unit
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            ProfileEditScreen(
                state = state,
                saveLabel = saveLabel,
                onSave = onSave
            )
        }
    }
}
