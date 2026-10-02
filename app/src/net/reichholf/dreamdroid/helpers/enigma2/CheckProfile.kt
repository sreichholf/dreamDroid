/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import java.net.HttpURLConnection
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.enigma.ReceiverApi
import net.reichholf.dreamdroid.enigma.ReceiverDetector
import net.reichholf.dreamdroid.enigma.ReceiverFlavor
import net.reichholf.dreamdroid.enigma.WebIfCapabilities
import net.reichholf.dreamdroid.ui.text.UiText

object CheckProfile {
    const val LOG_TAG: String = "CheckProfile"

    val FEATURE_EPGNOWNEXT_VERSION: IntArray = intArrayOf(1, 7, 0)

    val FEATURE_POST_REQEUEST: IntArray = intArrayOf(1, 7, 3)

    val FEATURE_SLEEPTIMER_VERSION: IntArray = intArrayOf(1, 6, 5)

    val REQUIRED_VERSION: IntArray = intArrayOf(1, 6, 5)

    /**
     * Asks [profile]'s receiver through [api] for its device info, tells its flavor with
     * [detector], and keeps the web interface capabilities that follow in [capabilities]. A
     * device-info answer cached in [profiles] stands in for asking, with the flavor cached
     * next to it.
     *
     * The Dreambox version table applies only to a Dreambox web interface. OpenWebif's version
     * numbers are its own; a receiver that says OpenWebif but whose `/api` does not confirm it
     * keeps the Dreambox client with the default capabilities and no version warning.
     */
    suspend fun checkProfile(
        profile: Profile,
        api: ReceiverApi,
        detector: ReceiverDetector,
        profiles: ProfileRepository,
        capabilities: WebIfCapabilitiesRepository
    ): ProfileCheckResult {
        val host = profile.host ?: return ProfileCheckResult()
        if (host.contains(" ")) {
            return ProfileCheckResult(
                hasError = true,
                errorTextId = R.string.illegal_host,
                failure = EnigmaFailure.Unreachable(
                    EnigmaFailure.UnreachableReason.IllegalHost,
                    host
                )
            )
        }
        val port = profile.port
        if (port <= 0 || port > 65535) {
            return ProfileCheckResult(
                hasError = true,
                errorTextId = R.string.port_out_of_range,
                failure = EnigmaFailure.Unreachable(
                    EnigmaFailure.UnreachableReason.IllegalHost,
                    port.toString()
                )
            )
        }

        val cached = profiles.deviceInfo(profile)
        val deviceInfo = cached ?: api.deviceInfo().let { fetched ->
            fetched.error?.let { error -> return connectionError(error.failure.ipRejected()) }
            fetched.value
        }
        if (deviceInfo == null || deviceInfo.isEmpty()) {
            profiles.setDeviceInfo(profile, null)
            return ProfileCheckResult(
                hasError = true,
                errorTextId = R.string.get_content_error,
                failure = EnigmaFailure.Parse
            )
        }
        val flavor = if (cached != null) {
            profiles.flavor(profile)
        } else {
            val detected = detector.detect(deviceInfo)
            detected.error?.let { error -> return connectionError(error.failure) }
            detected.value
        }
        // Before the device info, which wakes callers that then build a client from these.
        val version = deviceInfo.interfaceVersion.ifEmpty { "0" }
        capabilities.set(
            profile,
            when (flavor) {
                ReceiverFlavor.OpenWebif -> OPEN_WEBIF_CAPABILITIES
                ReceiverFlavor.DreamboxWebIf -> dreamboxCapabilities(version)
                null -> WebIfCapabilities()
            }
        )
        profiles.setDeviceInfo(profile, deviceInfo, flavor)
        if (flavor == ReceiverFlavor.DreamboxWebIf && checkVersion(version) < 0) {
            return ProfileCheckResult(
                hasError = true,
                isSoftError = true,
                errorTextId = R.string.version_too_low
            )
        }
        return ProfileCheckResult()
    }

    private fun connectionError(failure: EnigmaFailure) = ProfileCheckResult(
        hasError = true,
        errorTextId = R.string.connection_error,
        errorText = failure.userMessageText().takeUnless { it is UiText.Raw && it.text.isBlank() },
        failure = failure
    )

    /**
     * A 403 on the device info is OpenWebif refusing the address (plugin/httpserver.py:386-388
     * at e46534f), also before the flavor is known and the Dreambox client asks. The Dreambox
     * web interface sends no 403 of its own: it answers 401, 404 or 500 (opendreambox
     * enigma2-plugins webinterface).
     */
    private fun EnigmaFailure.ipRejected(): EnigmaFailure =
        if (this is EnigmaFailure.Http && code == HttpURLConnection.HTTP_FORBIDDEN) {
            EnigmaFailure.IpRejected
        } else {
            this
        }

    fun checkVersion(version: String): Int = checkVersion(version, REQUIRED_VERSION)

    fun checkVersion(version: String, required: IntArray): Int {
        val parts = version.split("\\.".toRegex()).toTypedArray()

        for (i in required.indices) {
            var cur = 0
            val req = required[i]

            if (parts.size >= i + 1) {
                try {
                    cur = parts[i].toInt()
                } catch (_: NumberFormatException) {
                }
            }

            when {
                cur == req -> {
                    if (i + 1 == required.size) {
                        return 0
                    }
                }

                cur > req -> return 1

                else -> return -1
            }
        }

        return -1
    }

    /** OpenWebif has now/next and the sleep timer, and accepts GET and POST. */
    private val OPEN_WEBIF_CAPABILITIES = WebIfCapabilities(
        nowNext = true,
        sleepTimer = true,
        postRequest = true
    )

    /**
     * What a Dreambox web interface of [version] can do. A failed fetch must not get here, so
     * a later Offline probe does not hide Sleep Timer on a box that already proved it can do
     * it.
     */
    internal fun dreamboxCapabilities(version: String): WebIfCapabilities = WebIfCapabilities(
        nowNext = checkVersion(version, FEATURE_EPGNOWNEXT_VERSION) >= 0,
        sleepTimer = checkVersion(version, FEATURE_SLEEPTIMER_VERSION) >= 0,
        postRequest = checkVersion(version, FEATURE_POST_REQEUEST) >= 0
    )
}
