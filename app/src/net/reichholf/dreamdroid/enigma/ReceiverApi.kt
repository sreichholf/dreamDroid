package net.reichholf.dreamdroid.enigma

import java.io.File
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerList
import net.reichholf.dreamdroid.enigma.autotimer.PreviewOutcome
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * One receiver's web interface, over one connection to one profile. Built per operation by
 * [ReceiverApiFactory]; see docs/openwebif.md for the per-webif implementations.
 *
 * The services, EPG, timer, movie, stream, location and tag calls take and return domain
 * types; how they map to requests and URLs is up to the implementation. The other calls still
 * take request parameters until their areas move behind this interface too.
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

    suspend fun getCurrent(): EnigmaResponse<CurrentService>

    suspend fun getDeviceInfo(): EnigmaResponse<DeviceInfo>

    suspend fun getSignal(): EnigmaResponse<Signal>

    /** A screenshot's image bytes, or no value when the receiver sent no image. */
    suspend fun getScreenshot(grabParams: List<NameValuePair>): EnigmaResponse<ByteArray>

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

    suspend fun setVolume(params: List<NameValuePair>): EnigmaResponse<Volume>

    suspend fun setPowerState(params: List<NameValuePair>): EnigmaResponse<PowerState>

    suspend fun sleepTimer(params: List<NameValuePair>): EnigmaResponse<SleepTimer>

    /** The path of each web interface plugin. */
    suspend fun getWebExternals(): EnigmaResponse<List<String>>

    /** Satellite roots of the bouquet editor for `mode` (0 TV, 1 radio). */
    suspend fun getBouquetEditorSatellites(
        params: List<NameValuePair>
    ): EnigmaResponse<List<Service>>

    /** The AutoTimer plugin's list. A config the box cannot load is a box rejection. */
    suspend fun getAutoTimers(): EnigmaResponse<AutoTimerList>

    // Mutations below: a rejected command has a value and a BoxRejected error.
    suspend fun zap(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun remoteCommand(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun sendMessage(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

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

    // Bouquet editor. The box applies each edit immediately.
    suspend fun addBouquet(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun removeBouquet(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun moveBouquet(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun addServiceToBouquet(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun removeBouquetService(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun moveBouquetService(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun renameBouquetEntry(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun addBouquetMarker(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    suspend fun backupBouquets(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    /** What the AutoTimer [id] would record now; the plugin skips disabled ones. */
    suspend fun testAutoTimer(id: Int): EnigmaResponse<PreviewOutcome>

    suspend fun editAutoTimer(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    /** Removes an AutoTimer. The plugin answers True even for an unknown id. */
    suspend fun removeAutoTimer(params: List<NameValuePair>): EnigmaResponse<SimpleResult>

    /** Runs all enabled AutoTimers now; the reply is the plugin's summary. */
    suspend fun runAutoTimers(): EnigmaResponse<SimpleResult>
}
