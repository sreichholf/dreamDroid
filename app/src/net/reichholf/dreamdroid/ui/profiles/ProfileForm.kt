package net.reichholf.dreamdroid.ui.profiles

import androidx.lifecycle.SavedStateHandle
import java.io.Serializable
import kotlinx.coroutines.CoroutineScope
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.ui.text.SavedTextField

/**
 * The switches of a profile being edited. The typed fields are [ProfileTextFields].
 * Serializable so a ViewModel can keep it in its `SavedStateHandle`.
 */
data class ProfileForm(
    val ssl: Boolean = false,
    val trustAllCerts: Boolean = false,
    val login: Boolean = false,
    val streamLogin: Boolean = false,
    val fileSsl: Boolean = false,
    val fileLogin: Boolean = false,
    val simpleRemote: Boolean = false,
    val defaultOnNoWifi: Boolean = false,
    val encoderStream: Boolean = false,
    val zapAndStream: Boolean = false,
    val encoderLogin: Boolean = false
) : Serializable {
    companion object {
        fun from(profile: Profile): ProfileForm = ProfileForm(
            ssl = profile.ssl,
            trustAllCerts = profile.allCertsTrusted,
            login = profile.login,
            streamLogin = profile.streamLogin,
            fileSsl = profile.fileSsl,
            fileLogin = profile.fileLogin,
            simpleRemote = profile.simpleRemote,
            defaultOnNoWifi = profile.isDefaultProfileOnNoWifi,
            encoderStream = profile.encoderStream,
            zapAndStream = profile.zapAndStream,
            encoderLogin = profile.encoderLogin
        )
    }
}

/**
 * The typed fields of a profile being edited, owned by its ViewModel. Numbers stay text
 * until [applyTo], so a half-typed port survives. With a [handle], the text survives
 * process death; [onHostEdit] runs when the user changes the host.
 */
class ProfileTextFields(
    private val scope: CoroutineScope,
    private val handle: SavedStateHandle? = null,
    onHostEdit: () -> Unit = {}
) {
    private fun textField(key: String, onEdit: (String) -> Unit = {}) =
        SavedTextField(scope, handle, "profile_text_$key", onEdit = onEdit)

    val name = textField("name")
    val host = textField("host") { onHostEdit() }
    val streamHost = textField("stream_host")
    val port = textField("port")
    val streamPort = textField("stream_port")
    val filePort = textField("file_port")
    val user = textField("user")
    val pass = textField("pass")
    val ssid = textField("ssid")
    val encoderPath = textField("encoder_path")
    val encoderPort = textField("encoder_port")
    val encoderUser = textField("encoder_user")
    val encoderPass = textField("encoder_pass")
    val encoderVideoBitrate = textField("encoder_video_bitrate")
    val encoderAudioBitrate = textField("encoder_audio_bitrate")

    fun fill(profile: Profile) {
        name.set(profile.name.orEmpty())
        host.set(profile.host.orEmpty())
        streamHost.set(profile.streamHost.orEmpty())
        port.set(profile.port.toString())
        streamPort.set(profile.streamPort.toString())
        filePort.set(profile.filePort.toString())
        user.set(profile.user.orEmpty())
        pass.set(profile.pass.orEmpty())
        ssid.set(profile.ssid.orEmpty())
        encoderPath.set(profile.encoderPath.orEmpty())
        encoderPort.set(profile.encoderPort.toString())
        encoderUser.set(profile.encoderUser.orEmpty())
        encoderPass.set(profile.encoderPass.orEmpty())
        encoderVideoBitrate.set(profile.encoderVideoBitrate.toString())
        encoderAudioBitrate.set(profile.encoderAudioBitrate.toString())
    }

    /** Switching https moves the port between 80 and 443. */
    fun onSslChanged(checked: Boolean) {
        port.set(if (checked) "443" else "80")
    }

    fun applyTo(profile: Profile, form: ProfileForm) {
        profile.name = name.text
        profile.host = host.text.trim()
        profile.streamHost = streamHost.text.trim()
        profile.setPort(port.text, form.ssl, form.trustAllCerts)
        profile.setStreamPort(streamPort.text)
        profile.setFilePort(filePort.text)
        profile.login = form.login
        profile.streamLogin = form.streamLogin
        profile.fileLogin = form.fileLogin
        profile.fileSsl = form.fileSsl
        profile.user = user.text
        profile.pass = pass.text
        profile.simpleRemote = form.simpleRemote
        profile.ssid = ssid.text.trim()
        profile.isDefaultProfileOnNoWifi = form.defaultOnNoWifi
        profile.encoderStream = form.encoderStream
        profile.zapAndStream = form.zapAndStream
        profile.encoderPath = encoderPath.text
        profile.setEncoderPort(encoderPort.text)
        profile.encoderLogin = form.encoderLogin
        profile.encoderUser = encoderUser.text
        profile.encoderPass = encoderPass.text
        profile.setEncoderAudioBitrate(encoderAudioBitrate.text)
        profile.setEncoderVideoBitrate(encoderVideoBitrate.text)
    }
}
