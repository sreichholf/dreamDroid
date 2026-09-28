package net.reichholf.dreamdroid.data

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the documents the user picked for a backup. [uri] is the picker's
 * content URI as a string.
 */
interface BackupDocuments {
    /** The document's text, or null when it cannot be read. */
    suspend fun read(uri: String): String?

    /** False when [text] could not be written. */
    suspend fun write(uri: String, text: String): Boolean
}

class ContentResolverBackupDocuments @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BackupDocuments {
    override suspend fun read(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                stream.bufferedReader().readText()
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
