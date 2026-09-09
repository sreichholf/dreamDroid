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
        streamHost = profile.streamHostValue.orEmpty()
        ssl = profile.isSsl
        trustAllCerts = profile.isAllCertsTrusted
        port = profile.portString
        streamPort = profile.streamPortString
        filePort = profile.filePortString
        login = profile.isLogin
        streamLogin = profile.isStreamLogin
        user = profile.user.orEmpty()
        pass = profile.pass.orEmpty()
        fileLogin = profile.isFileLogin
        fileSsl = profile.isFileSsl
        simpleRemote = profile.isSimpleRemote
        ssid = profile.ssid.orEmpty()
        defaultOnNoWifi = profile.isDefaultProfileOnNoWifi
        encoderStream = profile.isEncoderStream
        encoderPath = profile.encoderPath.orEmpty()
        encoderPort = profile.encoderPortString
        encoderLogin = profile.isEncoderLogin
        encoderUser = profile.encoderUser.orEmpty()
        encoderPass = profile.encoderPass.orEmpty()
        encoderVideoBitrate = profile.encoderVideoBitrateString
        encoderAudioBitrate = profile.encoderAudioBitrateString
    }

    fun applyTo(profile: Profile) {
        profile.name = name
        profile.setHost(host.trim())
        profile.setStreamHost(streamHost.trim())
        profile.setPort(port, ssl, trustAllCerts)
        profile.setStreamPort(streamPort)
        profile.setFilePort(filePort)
        profile.setLogin(login)
        profile.setStreamLogin(streamLogin)
        profile.setFileLogin(fileLogin)
        profile.setFileSsl(fileSsl)
        profile.user = user
        profile.pass = pass
        profile.setSimpleRemote(simpleRemote)
        profile.ssid = ssid.trim()
        profile.setDefaultProfileOnNoWifi(defaultOnNoWifi)
        profile.setEncoderStream(encoderStream)
        profile.encoderPath = encoderPath
        profile.setEncoderPort(encoderPort)
        profile.setEncoderLogin(encoderLogin)
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
        @JvmStatic
        fun fromProfile(profile: Profile): ProfileEditState {
            return ProfileEditState().also { it.loadFrom(profile) }
        }
    }
}

fun ComposeView.bindProfileEditScreen(
    state: ProfileEditState,
    saveLabel: String,
    onSave: () -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            ProfileEditScreen(
                state = state,
                saveLabel = saveLabel,
                onSave = onSave,
            )
        }
    }
}
