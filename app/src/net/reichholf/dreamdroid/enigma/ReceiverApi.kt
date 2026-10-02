package net.reichholf.dreamdroid.enigma

import java.io.File
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerId
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerList
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerWrite
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.helpers.EnigmaHttpError

/**
 * One receiver's web interface, over one connection to one profile. Built per operation by
 * [ReceiverApiFactory]; see docs/openwebif.md for the per-webif implementations.
 *
 * Every call takes and returns domain types; how they map to requests and URLs is up to the
 * implementation.
 */
interface ReceiverApi {
    /** Members of [containerRef]: a bouquet index, a bouquet, a provider, or a folder. */
    suspend fun services(containerRef: String): EnigmaResponse<List<Service>>

    /**
     * Now and next of each service in [bouquetRef] the receiver has EPG for: no markers or
     * folders. [bouquetRef] may be any container [services] lists, such as a provider.
     */
    suspend fun epgNowNext(bouquetRef: String): EnigmaResponse<List<ServiceNowNext>>

    /** One programme per service of [bouquetRef]: the one running at [atSec]. */
    suspend fun epgAt(bouquetRef: String, atSec: Long): EnigmaResponse<List<Event>>

    /** The schedule of [serviceRef] from now on. */
    suspend fun serviceEpg(serviceRef: String): EnigmaResponse<List<Event>>

    /** The programmes of [serviceRef] running between [beginSec] and [endSec]. */
    suspend fun serviceEpg(
        serviceRef: String,
        beginSec: Long,
        endSec: Long
    ): EnigmaResponse<List<Event>>

    /** The programmes of every service in [bouquetRef] from [startSec] to [endSec]. */
    suspend fun epgMulti(
        bouquetRef: String,
        startSec: Long,
        endSec: Long
    ): EnigmaResponse<List<Event>>

    /** Programmes whose title matches [query]. */
    suspend fun epgSearch(query: String): EnigmaResponse<List<Event>>

    /** The service the receiver is tuned to, with its now and next event. */
    suspend fun currentService(): EnigmaResponse<CurrentService>

    /** The receiver's hardware, image and web interface versions. */
    suspend fun deviceInfo(): EnigmaResponse<DeviceInfo>

    /** The tuner's signal on the current service. */
    suspend fun signal(): EnigmaResponse<Signal>

    /**
     * A JPEG of video and OSD at the receiver's resolution, or no value when the receiver
     * sent no image.
     */
    suspend fun screenshot(): EnigmaResponse<ByteArray>

    /** The receiver's timers. */
    suspend fun timers(): EnigmaResponse<List<Timer>>

    /**
     * Recordings in [location], or in the receiver's default location when it is empty,
     * filtered by [tags] unless that is empty.
     */
    suspend fun movies(location: String, tags: List<String>): EnigmaResponse<List<Movie>>

    /** The folders the receiver records into. */
    suspend fun locations(): EnigmaResponse<List<String>>

    /** The tags the receiver knows for timers and recordings. */
    suspend fun tags(): EnigmaResponse<List<String>>

    /** The URL a player opens for the live service [serviceRef]. */
    fun liveStreamUrl(serviceRef: String): String

    /** The URL a player opens for the recording [movie]. */
    fun recordingStreamUrl(movie: Movie): String

    /**
     * The web interface's URL of the recording file at [path], without credentials: only
     * usable when the profile needs no login.
     */
    fun recordingFileUrl(path: String): String

    /**
     * Copies the recording file at [path] into [destination], with the profile's login. The
     * failure, or null once [destination] holds the file.
     */
    suspend fun downloadRecording(path: String, destination: File): EnigmaHttpError?

    /** Runs the volume [command]; the answer carries the new level. */
    suspend fun setVolume(command: VolumeCommand): EnigmaResponse<Volume>

    /** Runs the power [command]; the answer carries the new state. */
    suspend fun setPowerState(command: PowerCommand): EnigmaResponse<PowerState>

    /** The sleep timer. */
    suspend fun sleepTimer(): EnigmaResponse<SleepTimer>

    /**
     * Sets the sleep timer to [minutes] from now and [action] (`standby` or `shutdown`),
     * switched on or off by [enabled]; the answer carries the stored timer.
     */
    suspend fun setSleepTimer(
        minutes: String?,
        action: String?,
        enabled: Boolean
    ): EnigmaResponse<SleepTimer>

    /** Whether the receiver has [plugin] installed. */
    suspend fun hasPlugin(plugin: ReceiverPlugin): EnigmaResponse<Boolean>

    /** The AutoTimer plugin's list. A config the box cannot load is a box rejection. */
    suspend fun autoTimers(): EnigmaResponse<AutoTimerList>

    // Mutations below: a rejected command has a value and a BoxRejected error.

    /** Zaps the receiver to [reference], a service or a recording. */
    suspend fun zap(reference: String): EnigmaResponse<SimpleResult>

    /**
     * Presses the remote-control key [keyCode] (a Linux input key code) on the standard remote
     * when [simpleRemote], else on the advanced one; held when [longPress].
     */
    suspend fun remoteCommand(
        keyCode: Int,
        simpleRemote: Boolean,
        longPress: Boolean
    ): EnigmaResponse<SimpleResult>

    /**
     * Shows [text] on the receiver's screen as a message of [type] (0 yes/no, 1 info,
     * 2 warning, 3 error) for [timeout] seconds.
     */
    suspend fun sendMessage(
        text: String?,
        type: String?,
        timeout: String?
    ): EnigmaResponse<SimpleResult>

    /** Plays [reference], a media player service reference, on the receiver. */
    suspend fun playMedia(reference: String): EnigmaResponse<SimpleResult>

    suspend fun deleteMovie(movie: Movie): EnigmaResponse<SimpleResult>

    /** Adds a timer for [event]; the receiver fills it in from its EPG. */
    suspend fun addTimerForEvent(event: Event): EnigmaResponse<SimpleResult>

    /** Adds [timer] as a new timer. */
    suspend fun addTimer(timer: Timer): EnigmaResponse<SimpleResult>

    /** Replaces the timer [old], found by its service, begin and end, with [new]. */
    suspend fun editTimer(old: Timer, new: Timer): EnigmaResponse<SimpleResult>

    /** Deletes [timer], found by its service, begin and end. */
    suspend fun deleteTimer(timer: Timer): EnigmaResponse<SimpleResult>

    /** Removes the finished timers. */
    suspend fun cleanupTimers(): EnigmaResponse<SimpleResult>

    // Bouquet editor plugin. The box applies each edit immediately.

    /** Satellite folders of [mode] to add services from. */
    suspend fun bouquetEditorSatellites(mode: BouquetMode): EnigmaResponse<List<Service>>

    /** Adds bouquet [name] to the [mode] index; the box appends " (TV)" or " (Radio)". */
    suspend fun addBouquet(mode: BouquetMode, name: String): EnigmaResponse<SimpleResult>

    suspend fun removeBouquet(mode: BouquetMode, bouquetRef: String): EnigmaResponse<SimpleResult>

    /** Moves [bouquetRef] to the 0-based [position] of the [mode] index. */
    suspend fun moveBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        position: Int
    ): EnigmaResponse<SimpleResult>

    /** Renames [bouquetRef] in place; its reference stays. */
    suspend fun renameBouquet(
        mode: BouquetMode,
        bouquetRef: String,
        newName: String
    ): EnigmaResponse<SimpleResult>

    /** Appends [serviceRef] to [bouquetRef]. */
    suspend fun addServiceToBouquet(
        bouquetRef: String,
        serviceRef: String
    ): EnigmaResponse<SimpleResult>

    suspend fun removeBouquetService(
        bouquetRef: String,
        serviceRef: String
    ): EnigmaResponse<SimpleResult>

    /** Moves [serviceRef] to the 0-based [position] of [bouquetRef]; markers count. */
    suspend fun moveBouquetService(
        mode: BouquetMode,
        bouquetRef: String,
        serviceRef: String,
        position: Int
    ): EnigmaResponse<SimpleResult>

    /**
     * Renames [serviceRef] in [bouquetRef]. The box replaces the entry, so its reference
     * changes; it goes before [beforeRef] ("" appends).
     */
    suspend fun renameBouquetService(
        bouquetRef: String,
        serviceRef: String,
        beforeRef: String,
        newName: String
    ): EnigmaResponse<SimpleResult>

    /** Adds marker [name] to [bouquetRef] before [beforeRef] ("" appends). */
    suspend fun addBouquetMarker(
        bouquetRef: String,
        name: String,
        beforeRef: String
    ): EnigmaResponse<SimpleResult>

    /** Backs up the bouquets into the box-side file [fileName]. */
    suspend fun backupBouquets(fileName: String): EnigmaResponse<SimpleResult>

    // AutoTimer plugin.

    /** What the AutoTimer [id] would record now; the plugin skips disabled ones. */
    suspend fun testAutoTimer(id: AutoTimerId): EnigmaResponse<PreviewOutcome>

    /** Writes [write]: changes an AutoTimer or adds one. */
    suspend fun saveAutoTimer(write: AutoTimerWrite): EnigmaResponse<SimpleResult>

    /** Removes the AutoTimer [id]. The plugin answers True even for an unknown id. */
    suspend fun removeAutoTimer(id: AutoTimerId): EnigmaResponse<SimpleResult>

    /** Runs all enabled AutoTimers now; the reply is the plugin's summary. */
    suspend fun runAutoTimers(): EnigmaResponse<SimpleResult>
}
