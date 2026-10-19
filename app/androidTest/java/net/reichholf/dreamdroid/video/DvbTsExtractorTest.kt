package net.reichholf.dreamdroid.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.TsExtractor
import kotlin.math.min
import net.reichholf.dreamdroid.testutil.DVB_NON_IDR_STREAM
import net.reichholf.dreamdroid.testutil.loadStreamFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why [Media3Streams.dvbExtractors] sets its flags: on a stream without IDR frames and without
 * random access indicators, stock ExoPlayer marks no video sample as a keyframe, so it never
 * starts the video. Runs the TS extractor alone, without decoders.
 */
@OptIn(UnstableApi::class)
class DvbTsExtractorTest {
    @Test
    fun stockFlagsFindNoVideoKeyframe() {
        assertEquals(0, videoKeyframes(0))
    }

    @Test
    fun dvbFlagsTakeTheIFramesAsKeyframes() {
        val keyframes = videoKeyframes(Media3Streams.DVB_TS_FLAGS)
        assertTrue("no keyframe with the DVB flags", keyframes > 0)
    }

    /** Extracts the whole fixture with [flags] and counts the video samples marked keyframe. */
    private fun videoKeyframes(flags: Int): Int {
        val bytes = loadStreamFixture(DVB_NON_IDR_STREAM)
        var offset = 0
        // Unknown length: the extractor reads straight through instead of seeking for a duration.
        val input = DefaultExtractorInput(
            { buffer, bufferOffset, length ->
                if (offset == bytes.size) {
                    C.RESULT_END_OF_INPUT
                } else {
                    val read = min(length, bytes.size - offset)
                    System.arraycopy(bytes, offset, buffer, bufferOffset, read)
                    offset += read
                    read
                }
            },
            0,
            C.LENGTH_UNSET.toLong()
        )
        val output = KeyframeCounter()
        val extractor = TsExtractor(flags)
        extractor.init(output)
        val position = PositionHolder()
        while (extractor.read(input, position) != Extractor.RESULT_END_OF_INPUT) Unit
        return output.videoKeyframes
    }

    private class KeyframeCounter : ExtractorOutput {
        var videoKeyframes = 0

        override fun track(id: Int, type: Int): TrackOutput {
            val discard = DiscardingTrackOutput()
            if (type != C.TRACK_TYPE_VIDEO) return discard
            return object : ForwardingTrackOutput(discard) {
                override fun sampleMetadata(
                    timeUs: Long,
                    flags: Int,
                    size: Int,
                    offset: Int,
                    cryptoData: TrackOutput.CryptoData?
                ) {
                    if (flags and C.BUFFER_FLAG_KEY_FRAME != 0) videoKeyframes++
                    super.sampleMetadata(timeUs, flags, size, offset, cryptoData)
                }
            }
        }

        override fun endTracks() = Unit

        override fun seekMap(seekMap: SeekMap) = Unit
    }
}
