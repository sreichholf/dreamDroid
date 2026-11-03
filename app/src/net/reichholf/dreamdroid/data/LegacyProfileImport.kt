package net.reichholf.dreamdroid.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.room.PiconSeed

/**
 * The pre-Room `dreamdroid` SQLite file, as far as the profile import needs it.
 * `DatabaseHelper.source` provides it for the app; tests use a fake.
 */
interface LegacyProfileSource {
    /**
     * Profiles in the leftover file. Empty when it is missing or has no rows; null when it
     * exists but cannot be read, which a later start cannot fix by retrying.
     */
    suspend fun read(): List<Profile>?

    /** Deletes the leftover file. True when it is gone afterwards. */
    suspend fun delete(): Boolean
}

/** Outcome of [LegacyProfileImport.run]. */
data class LegacyImportResult(
    val imported: Int,
    /**
     * True when Room is the profile source: the leftover was imported, or there was
     * nothing left to import. The 1.x preferences can then be consumed.
     */
    val complete: Boolean,
    /**
     * True when a later start should run the import again. The caller must then not build
     * a profile from the 1.x preferences, because that row would make Room non-empty and
     * block the retry.
     */
    val retryable: Boolean = false
)

/**
 * Copies the pre-Room `dreamdroid` profiles into Room. All rows go in through one
 * [ProfileStore.transaction], so a failure leaves Room as it was and the file in place for
 * the next start. The rows keep their legacy ids and a retry runs on the rolled-back store,
 * so it adds each row once. The file is deleted only after the transaction committed.
 */
class LegacyProfileImport(private val store: ProfileStore) {
    suspend fun run(source: LegacyProfileSource, picons: PiconSeed): LegacyImportResult {
        if (store.profiles().isNotEmpty()) {
            return skipExisting(source)
        }
        val rows = readOrNull(source) ?: return LegacyImportResult(0, complete = false)
        if (rows.isEmpty()) {
            delete(source)
            return LegacyImportResult(0, complete = true)
        }
        try {
            store.transaction {
                rows.forEach {
                    picons.applyTo(it)
                    it.id = store.add(it).toInt()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(DreamDroid.LOG_TAG, "Legacy profile import failed, keeping the leftover", e)
            return LegacyImportResult(0, complete = false, retryable = true)
        }
        delete(source)
        return LegacyImportResult(rows.size, complete = true)
    }

    /** Room is the source already; a leftover without rows is stale and goes. */
    private suspend fun skipExisting(source: LegacyProfileSource): LegacyImportResult {
        val rows = readOrNull(source) ?: return LegacyImportResult(0, complete = false)
        if (rows.isNotEmpty()) {
            return LegacyImportResult(0, complete = false)
        }
        delete(source)
        return LegacyImportResult(0, complete = true)
    }

    private suspend fun readOrNull(source: LegacyProfileSource): List<Profile>? = try {
        source.read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(DreamDroid.LOG_TAG, "Cannot read the leftover profile database", e)
        null
    }

    private suspend fun delete(source: LegacyProfileSource) {
        try {
            if (!source.delete()) {
                Log.w(DreamDroid.LOG_TAG, "Leftover profile database not deleted")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(DreamDroid.LOG_TAG, "Cannot delete the leftover profile database", e)
        }
    }
}
