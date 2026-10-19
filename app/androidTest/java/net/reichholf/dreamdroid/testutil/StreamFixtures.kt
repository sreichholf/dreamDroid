package net.reichholf.dreamdroid.testutil

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/**
 * `androidTest/resources/stream/dvb_non_idr.ts`: 6 s of H.264 (320x240) with AAC, cut out of
 * an open-GOP encode the way a receiver stream starts, mid-GOP. It has no IDR frame at all,
 * only I-frames with recovery points, as many DVB broadcasts do, and no TS packet sets the
 * random access indicator, which not every broadcaster sets.
 */
const val DVB_NON_IDR_STREAM = "dvb_non_idr.ts"
const val DVB_STREAM_WIDTH = 320
const val DVB_STREAM_HEIGHT = 240

fun loadStreamFixture(name: String): ByteArray {
    val loader = checkNotNull(Thread.currentThread().contextClassLoader) {
        "No context ClassLoader for androidTest fixture stream/$name"
    }
    val stream = checkNotNull(
        loader.getResourceAsStream("stream/$name")
            ?: object {}.javaClass.getResourceAsStream("/stream/$name")
    ) { "Missing androidTest fixture stream/$name" }
    return stream.use { it.readBytes() }
}

/** A receiver's stream port: answers every request with [bytes] as an MPEG-TS stream. */
fun streamServer(bytes: ByteArray): MockWebServer = MockWebServer().apply {
    dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest) = MockResponse()
            .setHeader("Content-Type", "video/mp2t")
            .setBody(Buffer().write(bytes))
    }
}
