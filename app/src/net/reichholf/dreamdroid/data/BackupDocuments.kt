package net.reichholf.dreamdroid.data

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.Reader
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the documents the user picked for a backup. [uri] is the picker's
 * content URI as a string.
 */
interface BackupDocuments {
    /**
     * The document's text, or null when it cannot be read or is longer than [maxChars].
     * At most [maxChars] + 1 characters are read, so a large file is never loaded whole.
     */
    suspend fun read(uri: String, maxChars: Int): String?

    /** False when [text] could not be written. */
    suspend fun write(uri: String, text: String): Boolean
}

class ContentResolverBackupDocuments @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BackupDocuments {
    override suspend fun read(uri: String, maxChars: Int): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                stream.bufferedReader().readAtMost(maxChars)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to read backup $uri", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to read backup $uri", e)
            null
        }
    }

    override suspend fun write(uri: String, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(Uri.parse(uri))
            output?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        } catch (e: IOException) {
            Log.e(TAG, "Export write failed.", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Export write failed.", e)
            false
        }
    }

    private companion object {
        const val TAG = "BackupDocuments"
    }
}

/** The rest of this reader, or null when it holds more than [maxChars] characters. */
internal fun Reader.readAtMost(maxChars: Int): String? {
    val buffer = CharArray(maxChars + 1)
    var length = 0
    while (length < buffer.size) {
        val count = read(buffer, length, buffer.size - length)
        if (count < 0) {
            break
        }
        length += count
    }
    return if (length > maxChars) null else String(buffer, 0, length)
}
