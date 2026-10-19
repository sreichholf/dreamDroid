package net.reichholf.dreamdroid.video

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import net.reichholf.dreamdroid.video.Media3Streams.GroupSupport
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Media3StreamsTest {
    @Test
    fun decoderAndContainerFailuresAreForVlc() {
        assertTrue(
            Media3Streams.isUnsupported(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
        )
        assertTrue(Media3Streams.isUnsupported(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertFalse(
            Media3Streams.isUnsupported(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        )
    }

    @Test
    fun aStreamWithoutAPlayableAudioTrackIsMissingADecoder() {
        val mp2Only = listOf(
            GroupSupport(C.TRACK_TYPE_VIDEO, supported = true),
            GroupSupport(C.TRACK_TYPE_AUDIO, supported = false)
        )
        assertTrue(Media3Streams.missingDecoder(mp2Only))
    }

    @Test
    fun oneUsableTrackPerTypeIsEnough() {
        val withAac = listOf(
            GroupSupport(C.TRACK_TYPE_VIDEO, supported = true),
            GroupSupport(C.TRACK_TYPE_AUDIO, supported = false),
            GroupSupport(C.TRACK_TYPE_AUDIO, supported = true),
            GroupSupport(C.TRACK_TYPE_TEXT, supported = false)
        )
        assertFalse(Media3Streams.missingDecoder(withAac))
    }

    @Test
    fun mpeg2VideoWithoutADecoderIsMissingOne() {
        val mpeg2 = listOf(
            GroupSupport(C.TRACK_TYPE_VIDEO, supported = false),
            GroupSupport(C.TRACK_TYPE_AUDIO, supported = true)
        )
        assertTrue(Media3Streams.missingDecoder(mpeg2))
    }
}
