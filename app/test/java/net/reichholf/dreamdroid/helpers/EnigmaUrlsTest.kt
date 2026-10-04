package net.reichholf.dreamdroid.helpers

import java.net.URLEncoder
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.StreamMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnigmaUrlsTest {
    @Test
    fun page_httpHostAndEmptyQuery() {
        val url = EnigmaUrls.page(httpProfile(), "/web/about")
        assertEquals("http://box.local:80/web/about?", url)
    }

    @Test
    fun page_httpsWhenSsl() {
        val url = EnigmaUrls.page(httpsProfile(), "/web/about")
        assertEquals("https://box.local:443/web/about?", url)
    }

    @Test
    fun page_appendsEncodedQuery() {
        val params = listOf(NameValuePair("sRef", "1:0:1"))
        val url = EnigmaUrls.page(httpProfile(), "/web/about", params)
        assertEquals("http://box.local:80/web/about?sRef=1%3A0%3A1", url)
    }

    @Test
    fun page_doesNotInsertSecondQuestionMark() {
        val params = listOf(NameValuePair("sRef", "1:0:1"))
        val url = EnigmaUrls.page(httpProfile(), "/web/getservices?", params)
        assertEquals("http://box.local:80/web/getservices?sRef=1%3A0%3A1", url)
    }

    @Test
    fun stream_usesTheProfileHost() {
        val profile = streamProfile(streamHost = "dreamdroid.org")
        val url = EnigmaUrls.serviceStream(profile, "1:0:1")
        assertTrue(url.startsWith("http://dreamdroid.org:8001/"))
        assertFalse(url.contains("bunny"))
        val fileUrl = EnigmaUrls.fileStream(profile, "4097:0:0:0:0:0:0:0:0:0:", "a.ts")
        assertTrue(fileUrl.contains("dreamdroid.org:"))
        assertFalse(fileUrl.contains("bunny"))
    }

    @Test
    fun stream_usesEncoderWhenEnabled() {
        val profile = encoderProfile()
        val url = EnigmaUrls.stream(profile, "1:0:1")
        assertTrue(url.startsWith("rtsp://"))
        assertTrue(url.contains("box.local:554/stream?"))
        assertTrue(url.contains("ref=1%3A0%3A1"))
        assertTrue(url.contains("video_bitrate=2500"))
        assertTrue(url.contains("audio_bitrate=128"))
    }

    @Test
    fun encoderStream_embedsCredentialsWhenLoginEnabled() {
        val profile = encoderProfile(encoderLogin = true)
        val url = EnigmaUrls.encoderStream(profile, "1:0:1")
        assertTrue(url.startsWith("rtsp://enc:pw@box.local:554/"))
    }

    @Test
    fun serviceStream_httpOmitsUserInfo() {
        val profile = streamProfile(streamLogin = true)
        val url = EnigmaUrls.serviceStream(profile, "1:0:1")
        assertFalse(url.contains("root:secret@"))
        assertEquals("http://box.local:8001/1%3A0%3A1", url)
    }

    @Test
    fun serviceStream_usesStreamHostWhenSet() {
        val profile = streamProfile(streamHost = "stream.box")
        val url = EnigmaUrls.serviceStream(profile, "1:0:1")
        assertEquals("http://stream.box:8001/1%3A0%3A1", url)
    }

    @Test
    fun serviceStream_extractsEmbeddedHttpUrl() {
        val ref = "4097:0:1:0:0:0:0:0:0:0:" +
            URLEncoder.encode("http://cdn.example/live.ts", "utf-8")
        val url = EnigmaUrls.serviceStream(streamProfile(), ref)
        assertEquals("http://cdn.example/live.ts", url)
    }

    @Test
    fun fileStream_httpOmitsUserInfo() {
        val profile = fileProfile(fileLogin = true, fileSsl = false)
        val url = EnigmaUrls.fileStream(profile, "1:0:1", "/tmp/a.ts")
        assertFalse(url.contains("root:secret@"))
        assertEquals("http://box.local:80/file?file=%2Ftmp%2Fa.ts", url)
    }

    @Test
    fun fileStream_httpsKeepsUserInfo() {
        val profile = fileProfile(fileLogin = true, fileSsl = true, filePort = 443)
        val url = EnigmaUrls.fileStream(profile, "1:0:1", "/tmp/a.ts")
        assertEquals("https://root:secret@box.local:443/file?file=%2Ftmp%2Fa.ts", url)
    }

    @Test
    fun fileStream_encoderWhenRefIsService() {
        val profile = encoderProfile()
        val url = EnigmaUrls.fileStream(profile, "1:0:1", "/tmp/a.ts")
        assertTrue(url.startsWith("rtsp://"))
        assertTrue(url.contains("ref=1%3A0%3A1"))
    }

    @Test
    fun stream_directUsesTheStreamPort() {
        val url = EnigmaUrls.stream(streamProfile(), "1:0:1")
        assertEquals("http://box.local:8001/1%3A0%3A1", url)
    }

    @Test
    fun stream_transcodingUsesTheTranscodePort() {
        val profile = transcodingProfile()
        assertEquals("http://box.local:8002/1%3A0%3A1", EnigmaUrls.stream(profile, "1:0:1"))
    }

    @Test
    fun stream_transcodingHonorsStreamHostAndPort() {
        val profile = transcodingProfile().apply {
            streamHost = "stream.box"
            transcodePort = 8001
        }
        assertEquals("http://stream.box:8001/1%3A0%3A1", EnigmaUrls.stream(profile, "1:0:1"))
    }

    @Test
    fun fileStream_transcodingAsksTheTranscoderForTheFile() {
        val profile = transcodingProfile().apply {
            fileSsl = true
            filePort = 443
            fileLogin = true
        }
        val url = EnigmaUrls.fileStream(profile, "1:0:0:0:0:0:0:0:0:0:/hdd/a.ts", "/hdd/a.ts")
        assertEquals("http://box.local:8002/file?file=%2Fhdd%2Fa.ts", url)
    }

    @Test
    fun serviceStream_httpsWhenStreamSsl() {
        val profile = streamProfile().apply { streamSsl = true }
        assertEquals("https://box.local:8001/1%3A0%3A1", EnigmaUrls.stream(profile, "1:0:1"))
    }

    @Test
    fun serviceStream_httpsKeepsUserInfo() {
        val profile = streamProfile(streamLogin = true).apply {
            streamSsl = true
            streamPort = 443
        }
        assertEquals(
            "https://root:secret@box.local:443/1%3A0%3A1",
            EnigmaUrls.stream(profile, "1:0:1")
        )
    }

    @Test
    fun stream_transcodingOverHttpsWhenStreamSsl() {
        val profile = transcodingProfile().apply { streamSsl = true }
        assertEquals("https://box.local:8002/1%3A0%3A1", EnigmaUrls.stream(profile, "1:0:1"))
    }

    @Test
    fun fileStream_transcodingFollowsTheStreamSchemeAndLogin() {
        val profile = transcodingProfile().apply {
            streamSsl = true
            streamLogin = true
        }
        val url = EnigmaUrls.fileStream(profile, "1:0:0:0:0:0:0:0:0:0:/hdd/a.ts", "/hdd/a.ts")
        assertEquals("https://root:secret@box.local:8002/file?file=%2Fhdd%2Fa.ts", url)
    }

    @Test
    fun fileStream_directIgnoresStreamSsl() {
        val profile = fileProfile().apply { streamSsl = true }
        val url = EnigmaUrls.fileStream(profile, "1:0:1", "/tmp/a.ts")
        assertEquals("http://box.local:80/file?file=%2Ftmp%2Fa.ts", url)
    }

    @Test
    fun fileStream_directIgnoresTheTranscodePort() {
        val url = EnigmaUrls.fileStream(fileProfile(), "1:0:1", "/tmp/a.ts")
        assertEquals("http://box.local:80/file?file=%2Ftmp%2Fa.ts", url)
    }

    private fun transcodingProfile(): Profile = streamProfile().apply {
        streamMode = StreamMode.Transcoding
    }

    private fun httpProfile(login: Boolean = false, host: String = "box.local"): Profile =
        Profile().apply {
            this.host = host
            port = 80
            ssl = false
            this.login = login
            user = "root"
            pass = "secret"
        }

    private fun httpsProfile(): Profile = Profile().apply {
        host = "box.local"
        port = 443
        ssl = true
        login = false
    }

    private fun streamProfile(streamLogin: Boolean = false, streamHost: String? = ""): Profile =
        httpProfile().apply {
            this.streamLogin = streamLogin
            this.streamHost = streamHost
            streamPort = 8001
        }

    private fun fileProfile(
        fileLogin: Boolean = false,
        fileSsl: Boolean = false,
        filePort: Int = 80
    ): Profile = httpProfile().apply {
        this.fileLogin = fileLogin
        this.fileSsl = fileSsl
        this.filePort = filePort
    }

    private fun encoderProfile(encoderLogin: Boolean = false): Profile = httpProfile().apply {
        streamMode = StreamMode.Encoder
        this.encoderLogin = encoderLogin
        encoderUser = "enc"
        encoderPass = "pw"
        encoderPort = 554
        encoderPath = "stream"
        encoderVideoBitrate = 2500
        encoderAudioBitrate = 128
    }
}
