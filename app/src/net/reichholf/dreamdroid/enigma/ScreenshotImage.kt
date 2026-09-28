package net.reichholf.dreamdroid.enigma

private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
private val PNG_MAGIC = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A
)

/**
 * True when [bytes] start with JPEG SOI or a PNG signature. `/grab` answers some failures
 * with HTTP 200 and an HTML or XML body.
 */
internal fun looksLikeScreenshotImage(bytes: ByteArray): Boolean =
    hasMagic(bytes, JPEG_MAGIC) || hasMagic(bytes, PNG_MAGIC)

private fun hasMagic(bytes: ByteArray, magic: ByteArray): Boolean =
    bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }
