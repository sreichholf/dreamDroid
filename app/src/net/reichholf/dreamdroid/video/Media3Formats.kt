package net.reichholf.dreamdroid.video

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The receiver formats this device cannot decode with Media3. */
interface Media3Formats {
    /** Display names, in the order of [DVB_FORMATS]. */
    suspend fun unsupported(): List<String>

    companion object {
        /** What DVB receivers send: MIME type to the name the settings show. */
        val DVB_FORMATS: List<Pair<String, String>> = listOf(
            MimeTypes.VIDEO_MPEG2 to "MPEG-2",
            MimeTypes.VIDEO_H264 to "H.264",
            MimeTypes.VIDEO_H265 to "HEVC",
            MimeTypes.AUDIO_MPEG_L2 to "MP2",
            MimeTypes.AUDIO_AC3 to "AC3",
            MimeTypes.AUDIO_E_AC3 to "E-AC3",
            MimeTypes.AUDIO_AAC to "AAC"
        )
    }
}

/**
 * Asks Media3's own decoder selection, the one its renderers use. Counts decoders only: a TV
 * that passes AC3 through HDMI can still play it, so this may list more than fails.
 */
@Singleton
class MediaCodecMedia3Formats @Inject constructor() : Media3Formats {
    override suspend fun unsupported(): List<String> = withContext(Dispatchers.Default) {
        Media3Formats.DVB_FORMATS.filterNot { (mimeType, _) -> hasDecoder(mimeType) }
            .map { (_, name) -> name }
    }

    @OptIn(UnstableApi::class)
    private fun hasDecoder(mimeType: String): Boolean = try {
        MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, false, false).isNotEmpty()
    } catch (e: MediaCodecUtil.DecoderQueryException) {
        Log.w("Media3Formats", "Querying decoders for $mimeType failed", e)
        false
    }
}
