package net.reichholf.dreamdroid.enigma

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp

/**
 * Builds an [EnigmaClient] per operation. A client wraps one [EnigmaHttp], which is bound
 * to one profile and cancels its in-flight call when a second fetch starts, so it must not
 * be shared across screens.
 */
@Singleton
class EnigmaClientFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val okHttp: EnigmaOkHttp
) {
    // Resolved on first dump, which runs on the fetch thread: getCacheDir() touches the disk.
    private val xmlDumpDir = lazy { File(context.cacheDir, XML_DUMP_DIR) }

    /** A client for the active profile. */
    fun current(): EnigmaClient = EnigmaClient(currentHttp())

    /** A client for [profile], which need not be the active one. */
    fun forProfile(profile: Profile): EnigmaClient = EnigmaClient(http(profile))

    /** Raw HTTP for the active profile, for callers that parse the response themselves. */
    fun currentHttp(): EnigmaHttp = http(profiles.requireCurrent())

    /** Raw HTTP for [profile], for downloads and other non-XML requests. */
    fun http(profile: Profile): EnigmaHttp = EnigmaHttp(
        profile = profile,
        okHttp = okHttp,
        xmlDumpDir = xmlDumpDir.takeIf { profiles.dumpXml() }
    )

    private companion object {
        const val XML_DUMP_DIR = "xml"
    }
}
