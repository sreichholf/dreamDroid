/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.DeviceInfoParser
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.ui.text.UiText

object CheckProfile {
    const val LOG_TAG: String = "CheckProfile"

    val FEATURE_EPGNOWNEXT_VERSION: IntArray = intArrayOf(1, 7, 0)

    val FEATURE_POST_REQEUEST: IntArray = intArrayOf(1, 7, 3)

    val FEATURE_SLEEPTIMER_VERSION: IntArray = intArrayOf(1, 6, 5)

    val REQUIRED_VERSION: IntArray = intArrayOf(1, 6, 5)

    /**
     * Asks [profile]'s receiver over [http] for its device info and applies the web
     * interface features it reports. A device-info answer cached in [profiles] stands in
     * for asking.
     */
    fun checkProfile(
        profile: Profile,
        http: EnigmaHttp,
        profiles: ProfileRepository
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

        val xml = profiles.deviceInfo(profile)
            ?: when (val fetched = http.fetch(URIStore.DEVICE_INFO)) {
                is EnigmaHttpResult.Success -> fetched.text

                is EnigmaHttpResult.Failure -> return ProfileCheckResult(
                    hasError = true,
                    errorTextId = R.string.connection_error,
                    errorText = fetched.error.failure.userMessageText()
                        .takeUnless { it is UiText.Raw && it.text.isBlank() },
                    failure = fetched.error.failure
                )
            }

        val deviceInfo = DeviceInfoParser.parse(xml)
        if (deviceInfo == null || deviceInfo.isEmpty()) {
            profiles.setDeviceInfo(profile, null)
            return ProfileCheckResult(
                hasError = true,
                errorTextId = R.string.get_content_error,
                failure = EnigmaFailure.Parse
            )
        }
        profiles.setDeviceInfo(profile, xml)
        val version = deviceInfo.interfaceVersion.ifEmpty { "0" }
        applyWebInterfaceFeatures(version)
        if (checkVersion(version) < 0) {
            return ProfileCheckResult(
                hasError = true,
                isSoftError = true,
                errorTextId = R.string.version_too_low
            )
        }
        return ProfileCheckResult()
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

    internal data class WebInterfaceFeatures(
        val sleepTimer: Boolean,
        val nowNext: Boolean,
        val postRequest: Boolean
    )

    internal fun webInterfaceFeatures(version: String): WebInterfaceFeatures = WebInterfaceFeatures(
        sleepTimer = checkVersion(version, FEATURE_SLEEPTIMER_VERSION) >= 0,
        nowNext = checkVersion(version, FEATURE_EPGNOWNEXT_VERSION) >= 0,
        postRequest = checkVersion(version, FEATURE_POST_REQEUEST) >= 0
    )

    /**
     * Sleep-timer / now-next / POST from a parsed web-interface version.
     * A failed fetch must not call this, so a later Offline probe does not hide
     * Sleep Timer on a box that already proved it can do it.
     */
    internal fun applyWebInterfaceFeatures(version: String) {
        val features = webInterfaceFeatures(version)
        if (features.sleepTimer) {
            DreamDroid.enableSleepTimer()
        } else {
            DreamDroid.disableSleepTimer()
        }
        if (features.nowNext) {
            DreamDroid.enableNowNext()
        } else {
            DreamDroid.disableNowNext()
        }
        DreamDroid.setFeaturePostRequest(features.postRequest)
    }
}
