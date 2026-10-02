package net.reichholf.dreamdroid.enigma

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp

/**
 * Builds a [ReceiverApi] per operation. A client wraps one [EnigmaHttp], which is bound to one
 * profile and cancels its in-flight call when a second fetch starts, so it must not be shared
 * across screens. Every profile gets a [DreamboxWebIfApi] for now, whatever its
 * [ReceiverFlavor]. Each client sees its profile's [WebIfCapabilities].
 */
@Singleton
class ReceiverApiFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val okHttp: EnigmaOkHttp,
    private val capabilities: WebIfCapabilitiesRepository
) {
    // Resolved on first dump, which runs on the fetch thread: getCacheDir() touches the disk.
    private val xmlDumpDir = lazy { File(context.cacheDir, XML_DUMP_DIR) }

    /**
     * A client for the active profile. [timeoutMillis] bounds each request; a long one suits
     * a call the box takes long to answer.
     */
    fun current(timeoutMillis: Int = EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS): ReceiverApi =
        client(profiles.requireCurrent(), timeoutMillis)

    /** A client for [profile], which need not be the active one. */
    fun forProfile(profile: Profile): ReceiverApi =
        client(profile, EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS)

    /** A [ReceiverDetector] for [profile]'s receiver, for one profile check. */
    fun detector(profile: Profile): ReceiverDetector =
        ReceiverDetector(http(profile, EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS))

    private fun client(profile: Profile, timeoutMillis: Int): ReceiverApi =
        DreamboxWebIfApi(http(profile, timeoutMillis), capabilities.of(profile))

    private fun http(profile: Profile, timeoutMillis: Int): EnigmaHttp = EnigmaHttp(
        profile = profile,
        okHttp = okHttp,
        capabilities = capabilities,
        xmlDumpDir = xmlDumpDir.takeIf { profiles.dumpXml() },
        timeoutMillis = timeoutMillis
    )

    private companion object {
        const val XML_DUMP_DIR = "xml"
    }
}
