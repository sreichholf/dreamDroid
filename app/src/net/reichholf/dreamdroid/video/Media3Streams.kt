package net.reichholf.dreamdroid.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/** How Media3 reads receiver streams, and which of its failures VLC may still play. */
@OptIn(UnstableApi::class)
object Media3Streams {
    /** A track group of [type] (`C.TRACK_TYPE_*`) and whether a decoder takes any of it. */
    data class GroupSupport(val type: Int, val supported: Boolean)

    /**
     * Extractors for DVB transport streams. A receiver stream starts anywhere in a long GOP,
     * and broadcasters often send non-IDR I-frames with recovery points instead of IDR frames.
     * Stock ExoPlayer takes such an I-frame as a keyframe only where the TS packet sets the
     * random access indicator; without it, ExoPlayer waits for an IDR frame that may never
     * come. These flags take any I-frame.
     */
    fun dvbExtractors(): DefaultExtractorsFactory =
        DefaultExtractorsFactory().setTsExtractorFlags(DVB_TS_FLAGS)

    /** The `DefaultTsPayloadReaderFactory` flags of [dvbExtractors]. */
    const val DVB_TS_FLAGS: Int =
        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS

    /** True when [errorCode] says Media3 cannot decode or read the stream at all. */
    fun isUnsupported(errorCode: Int): Boolean = errorCode in UNSUPPORTED

    /**
     * True when the stream has video, or audio, and no decoder for any of it: Media3 would
     * play it black or silent, where VLC may decode it.
     */
    fun missingDecoder(groups: List<GroupSupport>): Boolean =
        listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO).any { type ->
            val ofType = groups.filter { it.type == type }
            ofType.isNotEmpty() && ofType.none { it.supported }
        }

    private val UNSUPPORTED = setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
    )
}
