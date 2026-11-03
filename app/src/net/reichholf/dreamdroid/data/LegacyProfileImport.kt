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
     * exists but cannot be read, which the import settles by deleting it.
     */
    suspend fun read(): List<Profile>?

    /** Deletes the leftover file. True when it is gone afterwards. */
    suspend fun delete(): Boolean
}

/** Outcome of [LegacyProfileImport.run]. */
data class LegacyImportResult(
    val imported: Int,
    /**
     * True when the leftover is settled: its rows are in Room or it was deleted, so Room
     * is the profile source and the 1.x preferences can be consumed.
     */
    val complete: Boolean,
    /**
     * True when the import failed and a later start must run it again. The caller must then
     * not build a profile from the 1.x preferences: the leftover holds the profiles, and
     * that row would sit next to them.
     */
    val retryable: Boolean = false
)

/**
 * Copies the pre-Room `dreamdroid` profiles into Room and settles the leftover. The rows
 * Room does not have yet go in through one [ProfileStore.transaction], so a failure leaves
 * Room as it was and the file in place for the next start; the file is deleted only after
 * that transaction committed. A row counts as present when Room already has its id or its
 * name, so a leftover that overlaps a 1.15 Room database adds only the missing rows and a
 * retry adds none. Names are compared with Room's rows only: two distinct leftover profiles
 * that share a name both import.
 */
class LegacyProfileImport(private val store: ProfileStore) {
    suspend fun run(source: LegacyProfileSource, picons: PiconSeed): LegacyImportResult {
        val rows = readOrNull(source) ?: return dropUnreadable(source)
        val imported = try {
            store.transaction { importMissing(rows, picons) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(DreamDroid.LOG_TAG, "Legacy profile import failed, keeping the leftover", e)
            return LegacyImportResult(0, complete = false, retryable = true)
        }
        delete(source)
        return LegacyImportResult(imported, complete = true)
    }

    /**
     * Adds the rows Room does not have yet and returns how many. Runs inside [run]'s
     * transaction, so the rows it reads are the ones it writes against. The sets stay the
     * rows Room had when the transaction started, so rows of the leftover do not dedupe
     * each other.
     */
    private suspend fun importMissing(rows: List<Profile>, picons: PiconSeed): Int {
        val knownIds = HashSet<Int>()
        val knownNames = HashSet<String>()
        store.profiles().forEach { known ->
            known.id?.let { knownIds.add(it) }
            known.name?.takeIf { it.isNotBlank() }?.let { knownNames.add(it) }
        }
        var imported = 0
        for (row in rows) {
            val id = row.id
            val name = row.name?.takeIf { it.isNotBlank() }
            if ((id != null && id in knownIds) || (name != null && name in knownNames)) {
                continue
            }
            picons.applyTo(row)
            row.id = store.add(row).toInt()
            imported++
        }
        return imported
    }

    /**
     * A leftover that cannot be read cannot be imported on a later start either. Deleting
     * it lets the 1.x settings settle, so their receiver password stops outliving the
     * import.
     */
    private suspend fun dropUnreadable(source: LegacyProfileSource): LegacyImportResult {
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
