package net.reichholf.dreamdroid.data

import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.BouquetEntry
import net.reichholf.dreamdroid.enigma.BouquetMode
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.ReceiverApi
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.enigma.ReceiverPlugin
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.toBouquetEntry

/** The receiver edits go to: the active profile's id and address. */
data class BouquetReceiver(val profileId: Int?, val host: String?, val port: Int)

/**
 * One edit on the receiver. [backup] is the box-side backup this edit ran first, or null
 * when the editor session had already backed up. A failed backup did not stop the edit.
 */
data class BouquetEditResult(
    val response: EnigmaResponse<SimpleResult>,
    val backup: EnigmaResponse<SimpleResult>? = null
) {
    val succeeded: Boolean
        get() = response.succeeded
}

/**
 * Bouquet editing through the optional WebBouquetEditor plugin (`/bouqueteditor`). Lists
 * come from `/web/getservices`. Edits reach the box one at a time, in call order. The first
 * edit of an editor session ([resetBackup]) per profile writes a backup to the box's `/tmp`.
 * Unless the box rejected it, an edit drops the Room rosters it touched
 * ([ServiceRepository.onBouquetsEdited]): one cancelled or cut off after it was sent may
 * still have reached the box.
 */
@Singleton
class BouquetEditorRepository @Inject constructor(
    private val clients: ReceiverApiFactory,
    private val profiles: ProfileRepository,
    private val services: ServiceRepository
) {
    private val editMutex = Mutex()
    private val backedUpProfiles = Collections.synchronizedSet(HashSet<Int?>())

    /** The receiver edits go to now; null without an active profile. */
    fun currentReceiver(): BouquetReceiver? = profiles.current.value?.receiver()

    /** Emits the receiver edits go to, and again each time the active profile changes it. */
    val receiver: Flow<BouquetReceiver?> =
        profiles.current.map { it?.receiver() }.distinctUntilChanged()

    /** Whether the receiver has the plugin. Null value on failure. */
    suspend fun isAvailable(): EnigmaResponse<Boolean> =
        clients.current().hasPlugin(ReceiverPlugin.BouquetEditor)

    /** The bouquets of the [mode] index, in the box's order. */
    suspend fun bouquets(mode: BouquetMode): EnigmaResponse<List<BouquetEntry>> =
        list(root(mode), atRoot = true)

    /** The rows of [ref]: a bouquet's entries, or a source folder's services. */
    suspend fun entries(ref: String): EnigmaResponse<List<BouquetEntry>> = list(ref, atRoot = false)

    /** Satellite folders to add services from. */
    suspend fun satellites(mode: BouquetMode): EnigmaResponse<List<BouquetEntry>> {
        val response = clients.current().bouquetEditorSatellites(mode)
        return EnigmaResponse(
            response.value?.map {
                it.toBouquetEntry(atRoot = false)
            },
            response.error
        )
    }

    /** Provider folders to add services from; [entries] of one lists its services. */
    suspend fun providers(mode: BouquetMode): EnigmaResponse<List<BouquetEntry>> =
        entries(roots(mode)[PROVIDERS])

    /** Every service of [mode], by name. */
    suspend fun allServices(mode: BouquetMode): EnigmaResponse<List<BouquetEntry>> =
        entries(roots(mode)[ALL_SERVICES])

    /** Starts a new editor session: the next edit backs up again. */
    fun resetBackup() {
        backedUpProfiles.clear()
    }

    /** Adds bouquet [name]; the box appends " (TV)" or " (Radio)" and allows duplicates. */
    suspend fun addBouquet(mode: BouquetMode, name: String): BouquetEditResult =
        edit(listOf(root(mode))) { addBouquet(mode, name) }

    suspend fun removeBouquet(mode: BouquetMode, bouquetRef: String): BouquetEditResult =
        edit(listOf(root(mode), bouquetRef)) { removeBouquet(mode, bouquetRef) }

    /** Moves [bouquetRef] to the 0-based [position] of the index. */
    suspend fun moveBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        position: Int
    ): BouquetEditResult = edit(listOf(root(mode))) { moveBouquet(mode, bouquetRef, position) }

    /** Renames [bouquetRef] in place; its reference stays. */
    suspend fun renameBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        newName: String
    ): BouquetEditResult = edit(listOf(root(mode))) { renameBouquet(mode, bouquetRef, newName) }

    /**
     * Appends [refs] to [bouquetRef] in order. A rejected one (already in the bouquet) does
     * not stop the rest; the result is the first rejection, else the last answer. A request
     * the box did not answer stops the batch.
     */
    suspend fun addServices(bouquetRef: String, refs: List<String>): BouquetEditResult =
        editMutex.withLock {
            val client = clients.current()
            val backup = backupOnce(client)
            var rejected: EnigmaResponse<SimpleResult>? = null
            var last = EnigmaResponse<SimpleResult>(null)
            var touched = false
            invalidatingOnCancel(listOf(bouquetRef)) {
                for (ref in refs) {
                    last = client.addServiceToBouquet(bouquetRef, ref)
                    if (!last.rejected) {
                        touched = true
                    } else {
                        rejected = rejected ?: last
                    }
                    if (last.value == null) {
                        break
                    }
                }
            }
            if (touched) {
                services.onBouquetsEdited(listOf(bouquetRef))
            }
            val result = if (last.value == null) last else rejected ?: last
            BouquetEditResult(result, backup)
        }

    suspend fun removeService(bouquetRef: String, ref: String): BouquetEditResult =
        edit(listOf(bouquetRef)) { removeBouquetService(bouquetRef, ref) }

    /** Moves [ref] to the 0-based [position] of [bouquetRef]; markers count. */
    suspend fun moveService(
        mode: BouquetMode,
        bouquetRef: String,
        ref: String,
        position: Int
    ): BouquetEditResult = edit(listOf(bouquetRef)) {
        moveBouquetService(mode, bouquetRef, ref, position)
    }

    /**
     * Renames [ref] in [bouquetRef]. The box replaces the entry, so its reference changes;
     * [sRefBefore] is the next entry's reference ("" for the last) to keep its position.
     */
    suspend fun renameService(
        bouquetRef: String,
        ref: String,
        sRefBefore: String,
        newName: String
    ): BouquetEditResult = edit(listOf(bouquetRef)) {
        renameBouquetService(bouquetRef, ref, sRefBefore, newName)
    }

    /** Adds marker [name] before [sRefBefore] ("" appends). */
    suspend fun addMarker(bouquetRef: String, name: String, sRefBefore: String): BouquetEditResult =
        edit(listOf(bouquetRef)) { addBouquetMarker(bouquetRef, name, sRefBefore) }

    private suspend fun edit(
        editedRefs: List<String>,
        call: suspend ReceiverApi.() -> EnigmaResponse<SimpleResult>
    ): BouquetEditResult = editMutex.withLock {
        val client = clients.current()
        val backup = backupOnce(client)
        val response = invalidatingOnCancel(editedRefs) { client.call() }
        if (!response.rejected) {
            services.onBouquetsEdited(editedRefs)
        }
        BouquetEditResult(response, backup)
    }

    /** Runs [request]; if it is cancelled once sent, drops the caches of [refs] all the same. */
    private suspend fun <T> invalidatingOnCancel(refs: List<String>, request: suspend () -> T): T =
        try {
            request()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { services.onBouquetsEdited(refs) }
            throw e
        }

    /** Runs under [editMutex]. Tried once per profile and session, whatever the outcome. */
    private suspend fun backupOnce(client: ReceiverApi): EnigmaResponse<SimpleResult>? {
        if (!backedUpProfiles.add(profiles.requireCurrent().id)) {
            return null
        }
        val name = BACKUP_PREFIX + System.currentTimeMillis() / 1000L
        return client.backupBouquets(name)
    }

    private suspend fun list(ref: String, atRoot: Boolean): EnigmaResponse<List<BouquetEntry>> {
        val response = clients.current().services(ref)
        return EnigmaResponse(response.value?.map { it.toBouquetEntry(atRoot) }, response.error)
    }

    private fun roots(mode: BouquetMode): List<String> = when (mode) {
        BouquetMode.Tv -> services.tvRoots
        BouquetMode.Radio -> services.radioRoots
    }

    private fun root(mode: BouquetMode): String = roots(mode)[BOUQUETS]

    private companion object {
        const val BACKUP_PREFIX = "dreamdroid_"

        // Indexes into R.array.servicerefstv / servicerefsradio.
        const val BOUQUETS = 0
        const val PROVIDERS = 1
        const val ALL_SERVICES = 2
    }
}

private val EnigmaResponse<SimpleResult>.succeeded: Boolean
    get() = value != null && error == null

/** The box answered and turned the edit down, so nothing changed. */
private val EnigmaResponse<SimpleResult>.rejected: Boolean
    get() = value != null && error != null

private fun Profile.receiver() = BouquetReceiver(id, host, port)
