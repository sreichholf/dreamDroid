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
 * across screens. A profile detected as [ReceiverFlavor.OpenWebif] gets an [OpenWebifApi];
 * every other one, an undetected one included, a [DreamboxWebIfApi]. Each client sees its
 * profile's [WebIfCapabilities]. A Dreambox client sends timer requests through the VPS plugin
 * when told it is there ([VpsPlugin]); OpenWebif takes VPS on its own timer requests.
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
     * a call the box takes long to answer. [vpsPlugin] is what the caller knows of the VPS plugin.
     */
    fun current(
        timeoutMillis: Int = EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS,
        vpsPlugin: VpsPlugin = VpsPlugin.Absent
    ): ReceiverApi = client(profiles.requireCurrent(), timeoutMillis, vpsPlugin)

    /** A client for [profile], which need not be the active one. */
    fun forProfile(profile: Profile): ReceiverApi =
        client(profile, EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS, VpsPlugin.Absent)

    /** A [ReceiverDetector] for [profile]'s receiver, for one profile check. */
    fun detector(profile: Profile): ReceiverDetector =
        ReceiverDetector(http(profile, EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS))

    private fun client(profile: Profile, timeoutMillis: Int, vpsPlugin: VpsPlugin): ReceiverApi {
        val http = http(profile, timeoutMillis)
        return when (profiles.flavor(profile)) {
            ReceiverFlavor.OpenWebif -> OpenWebifApi(http)

            ReceiverFlavor.DreamboxWebIf, null ->
                DreamboxWebIfApi(http, capabilities.of(profile), vpsPlugin)
        }
    }

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
