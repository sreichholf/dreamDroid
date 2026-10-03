package net.reichholf.dreamdroid

/**
 * How a profile opens live TV and recordings. Room and backups store the entry name, so
 * renaming an entry needs a migration.
 */
enum class StreamMode {
    /** The receiver's stream port (8001) for live TV, `/file` on the file port for recordings. */
    Direct,

    /** The Dreambox RTSP encoder: [Profile.encoderPath] on [Profile.encoderPort]. */
    Encoder,

    /**
     * HTTP transcoding on [Profile.transcodePort], as OpenWebif images with the
     * TranscodingSetup plugin serve it (8002 by default). Live TV is `/<ref>`, recordings
     * `/file?file=<path>` on that port; the box applies its own transcoding settings.
     */
    Transcoding
}
