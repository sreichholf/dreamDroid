package net.reichholf.dreamdroid.ui.profiles

import java.io.Serializable
import net.reichholf.dreamdroid.Profile

/**
 * The editable fields of a [Profile], as typed. Numbers stay text until [applyTo], so a
 * half-typed port survives. Serializable so a ViewModel can keep it in its
 * `SavedStateHandle`.
 */
data class ProfileForm(
    val name: String = "",
    val host: String = "",
    val streamHost: String = "",
    val port: String = "80",
    val streamPort: String = "8001",
    val filePort: String = "80",
    val ssl: Boolean = false,
    val trustAllCerts: Boolean = false,
    val login: Boolean = false,
    val streamLogin: Boolean = false,
    val fileSsl: Boolean = false,
    val fileLogin: Boolean = false,
    val user: String = "",
    val pass: String = "",
    val simpleRemote: Boolean = false,
    val ssid: String = "",
    val defaultOnNoWifi: Boolean = false,
    val encoderStream: Boolean = false,
    val zapAndStream: Boolean = false,
    val encoderLogin: Boolean = false,
    val encoderPath: String = "stream",
    val encoderUser: String = "",
    val encoderPass: String = "",
    val encoderVideoBitrate: String = "2500",
    val encoderAudioBitrate: String = "128",
    val encoderPort: String = "554"
) : Serializable {
    /** Switching https moves the port between 80 and 443. */
    fun withSsl(checked: Boolean): ProfileForm =
        copy(ssl = checked, port = if (checked) "443" else "80")

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

    companion object {
        fun from(profile: Profile): ProfileForm = ProfileForm(
            name = profile.name.orEmpty(),
            host = profile.host.orEmpty(),
            streamHost = profile.streamHost.orEmpty(),
            ssl = profile.ssl,
            trustAllCerts = profile.allCertsTrusted,
            port = profile.port.toString(),
            streamPort = profile.streamPort.toString(),
            filePort = profile.filePort.toString(),
            login = profile.login,
            streamLogin = profile.streamLogin,
            user = profile.user.orEmpty(),
            pass = profile.pass.orEmpty(),
            fileLogin = profile.fileLogin,
            fileSsl = profile.fileSsl,
            simpleRemote = profile.simpleRemote,
            ssid = profile.ssid.orEmpty(),
            defaultOnNoWifi = profile.isDefaultProfileOnNoWifi,
            encoderStream = profile.encoderStream,
            zapAndStream = profile.zapAndStream,
            encoderPath = profile.encoderPath.orEmpty(),
            encoderPort = profile.encoderPort.toString(),
            encoderLogin = profile.encoderLogin,
            encoderUser = profile.encoderUser.orEmpty(),
            encoderPass = profile.encoderPass.orEmpty(),
            encoderVideoBitrate = profile.encoderVideoBitrate.toString(),
            encoderAudioBitrate = profile.encoderAudioBitrate.toString()
        )
    }
}
