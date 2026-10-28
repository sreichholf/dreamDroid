package net.reichholf.dreamdroid.testutil

import net.reichholf.dreamdroid.data.BackupDocuments

/**
 * Backup documents in memory, by URI. A file longer than the caller's limit reads as
 * missing, and [writable] false makes every write fail, as a revoked permission does.
 */
class MemoryBackupDocuments : BackupDocuments {
    val files = HashMap<String, String>()
    var writable = true

    override suspend fun read(uri: String, maxChars: Int): String? =
        files[uri]?.takeIf { it.length <= maxChars }

    override suspend fun write(uri: String, text: String): Boolean {
        if (writable) {
            files[uri] = text
        }
        return writable
    }
}
