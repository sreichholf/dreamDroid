package net.reichholf.dreamdroid.helpers

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/** Enigma2 webinterface and stream URL builders. No HTTP I/O. */
object EnigmaUrls {
    const val BIG_BUCK_BUNNY_URL: String =
        "https://dreamdroid.org/bunny/big_buck_bunny_720p_h264.mov"

    fun page(profile: Profile, uri: String, parameters: List<NameValuePair> = emptyList()): String {
        val path = withQuery(uri, parameters)
        return webPrefix(profile) + profile.host + ":" + profile.port + path
    }

    fun authed(
        profile: Profile,
        uri: String,
        parameters: List<NameValuePair> = emptyList()
    ): String {
        val path = withQuery(uri, parameters)
        var loginString = ""
        if (profile.login) {
            loginString = String.format("%s:%s@", profile.user, profile.pass)
        }
        return webPrefix(profile) + loginString + profile.host + ":" + profile.port + path
    }

    fun stream(profile: Profile, ref: String): String {
        if (profile.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        return if (profile.encoderStream) {
            encoderStream(profile, ref)
        } else {
            serviceStream(profile, ref)
        }
    }

    fun encoderStream(profile: Profile, ref: String): String {
        if (profile.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
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

    fun serviceStream(profile: Profile, ref: String): String {
        if (profile.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
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
        val streamLoginString = HttpUserInfo.embed(
            enabled = profile.streamLogin,
            user = profile.user,
            pass = profile.pass,
            scheme = "http"
        )
        return "http://" + streamLoginString + profile.streamHostOrHost + ":" +
            profile.streamPort + "/" + serviceRef
    }

    fun fileStream(profile: Profile, ref: String, fileName: String?): String {
        if (profile.host == "dreamdroid.org") {
            return BIG_BUCK_BUNNY_URL
        }
        if (profile.encoderStream && ref.startsWith("1:")) {
            return encoderStream(profile, ref)
        }
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("file", fileName))
        val parms = NameValuePair.toString(params)
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
