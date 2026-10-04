package net.reichholf.dreamdroid.helpers

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.StreamMode
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/** Enigma2 webinterface and stream URL builders. No HTTP I/O. */
object EnigmaUrls {
    fun page(profile: Profile, uri: String, parameters: List<NameValuePair> = emptyList()): String {
        val path = withQuery(uri, parameters)
        return webPrefix(profile) + profile.host + ":" + profile.port + path
    }

    /** Live stream URL for [ref] in the profile's [StreamMode]. */
    fun stream(profile: Profile, ref: String): String = when (profile.streamMode) {
        StreamMode.Direct -> serviceStream(profile, ref)
        StreamMode.Encoder -> encoderStream(profile, ref)
        StreamMode.Transcoding -> serviceStream(profile, ref, profile.transcodePort)
    }

    fun encoderStream(profile: Profile, ref: String): String {
        var encoded = ref
        try {
            encoded = URLEncoder.encode(ref, "utf-8").replace("+", "%20")
        } catch (_: UnsupportedEncodingException) {
        }
        var streamLoginString = ""
        if (profile.encoderLogin) {
            streamLoginString = profile.encoderUser + ":" + profile.encoderPass + "@"
        }
        return String.format(
            "rtsp://%s%s:%s/%s?ref=%s&video_bitrate=%s&audio_bitrate=%s",
            streamLoginString,
            profile.streamHostOrHost,
            profile.encoderPort,
            profile.encoderPath,
            encoded,
            profile.encoderVideoBitrate,
            profile.encoderAudioBitrate
        )
    }

    /**
     * `http://host:port/<ref>`, or https when [Profile.streamSsl] is set. OpenWebif's transcoder
     * takes the same shape on its own port (OpenWebif
     * `plugin/controllers/models/stream.py:83-90,128`); without query parameters it applies the
     * box's TranscodingSetup values.
     */
    fun serviceStream(profile: Profile, ref: String, port: Int = profile.streamPort): String {
        var serviceRef = ref
        if (serviceRef.contains("http")) {
            try {
                return URLDecoder.decode(
                    serviceRef.substring(serviceRef.indexOf("http")),
                    "utf-8"
                ).replace(" ", "%20")
            } catch (_: UnsupportedEncodingException) {
            }
        }
        try {
            serviceRef = URLEncoder.encode(serviceRef, "utf-8").replace("+", "%20")
        } catch (_: UnsupportedEncodingException) {
        }
        return streamOrigin(profile, port) + "/" + serviceRef
    }

    /**
     * Recording URL. [StreamMode.Transcoding] asks the transcoder for `/file`, as OpenWebif's
     * own m3u does (`stream.py:191-198,248`), with the live stream's scheme and login.
     */
    fun fileStream(profile: Profile, ref: String, fileName: String?): String {
        if (profile.streamMode == StreamMode.Encoder && ref.startsWith("1:")) {
            return encoderStream(profile, ref)
        }
        val parms = NameValuePair.toString(listOf(NameValuePair("file", fileName)))
        if (profile.streamMode == StreamMode.Transcoding) {
            return streamOrigin(profile, profile.transcodePort) + URIStore.FILE + parms
        }
        val fileScheme = if (profile.fileSsl) "https" else "http"
        val fileAuthString = HttpUserInfo.embed(
            enabled = profile.fileLogin,
            user = profile.user,
            pass = profile.pass,
            scheme = fileScheme
        )
        return filePrefix(profile) + fileAuthString + profile.streamHostOrHost + ":" +
            profile.filePort + URIStore.FILE + parms
    }

    /** `scheme://[user:pass@]host:port` of the HTTP stream ports. */
    private fun streamOrigin(profile: Profile, port: Int): String {
        val scheme = if (profile.streamSsl) "https" else "http"
        val userInfo = HttpUserInfo.embed(
            enabled = profile.streamLogin,
            user = profile.user,
            pass = profile.pass,
            scheme = scheme
        )
        return "$scheme://$userInfo${profile.streamHostOrHost}:$port"
    }

    private fun webPrefix(profile: Profile): String = if (profile.ssl) "https://" else "http://"

    private fun filePrefix(profile: Profile): String =
        if (profile.fileSsl) "https://" else "http://"

    private fun withQuery(uri: String, parameters: List<NameValuePair>): String {
        var path = uri
        val parms = NameValuePair.toString(parameters)
        if (!path.contains("?")) {
            path += "?"
        }
        return path + parms
    }
}
