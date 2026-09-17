/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2

import android.content.Context
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.DeviceInfoParser
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckEntry
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult

object CheckProfile {
    const val LOG_TAG: String = "CheckProfile"

    val FEATURE_EPGNOWNEXT_VERSION: IntArray = intArrayOf(1, 7, 0)

    val FEATURE_POST_REQEUEST: IntArray = intArrayOf(1, 7, 3)

    val REQUIRED_VERSION: IntArray = intArrayOf(1, 6, 5)

    var CURRENT_VERSION: IntArray = intArrayOf(0, 0, 0)

    fun checkProfile(profile: Profile, context: Context): ProfileCheckResult {
        CURRENT_VERSION = intArrayOf(0, 0, 0)
        DreamDroid.disableSleepTimer()
        DreamDroid.disableNowNext()

        val resultList = ArrayList<ProfileCheckEntry>()
        var hasError = false
        var isSoftError = false
        var errorTextId = -1
        var errorTextExt = ""
        var failure: EnigmaFailure? = null

        val host = profile.host

        if (host != null) {
            if (!host.contains(" ")) {
                resultList.add(entry(R.string.host, false, host))

                val port = profile.port
                if (port > 0 && port <= 65535) {
                    resultList.add(entry(R.string.port, false, port.toString()))
                    val http = EnigmaHttp(profile)
                    var xml = profile.cachedDeviceInfo
                    var fetchError: EnigmaHttpError? = null
                    if (xml == null) {
                        when (val fetched = http.fetch(URIStore.DEVICE_INFO)) {
                            is EnigmaHttpResult.Success -> xml = fetched.text
                            is EnigmaHttpResult.Failure -> fetchError = fetched.error
                        }
                    }

                    if (xml != null) {
                        val deviceInfo = DeviceInfoParser.parse(xml)

                        if (deviceInfo != null && !deviceInfo.isEmpty()) {
                            profile.cachedDeviceInfo = xml
                            resultList.add(
                                entry(R.string.device_name, false, deviceInfo.deviceName)
                            )

                            var version = deviceInfo.interfaceVersion
                            if (version.isEmpty()) {
                                version = "0"
                            }
                            val vc = checkVersion(version)
                            if (vc >= 0) {
                                val requiredForSleeptimer = intArrayOf(1, 6, 5)
                                if (checkVersion(version, requiredForSleeptimer) >= 0) {
                                    DreamDroid.enableSleepTimer()
                                }
                                if (checkVersion(version, FEATURE_EPGNOWNEXT_VERSION) >= 0) {
                                    DreamDroid.enableNowNext()
                                }
                                if (checkVersion(version, FEATURE_POST_REQEUEST) >= 0) {
                                    DreamDroid.setFeaturePostRequest(true)
                                } else {
                                    DreamDroid.setFeaturePostRequest(false)
                                }

                                resultList.add(entry(R.string.interface_version, false, version))
                            } else {
                                resultList.add(
                                    entry(
                                        R.string.interface_version,
                                        true,
                                        version,
                                        R.string.version_too_low
                                    )
                                )
                                hasError = true
                                isSoftError = true
                                errorTextId = R.string.version_too_low
                            }
                        } else {
                            profile.cachedDeviceInfo = null
                            resultList.add(
                                entry(
                                    R.string.connection,
                                    true,
                                    host.toString(),
                                    R.string.get_content_error
                                )
                            )
                            hasError = true
                            errorTextId = R.string.get_content_error
                            failure = EnigmaFailure.Parse
                        }
                    } else if (fetchError != null) {
                        val ext = fetchError.resolve(context)
                        resultList.add(
                            entry(
                                R.string.connection,
                                true,
                                host.toString(),
                                R.string.connection_error,
                                ext
                            )
                        )
                        hasError = true
                        errorTextId = R.string.connection_error
                        errorTextExt = ext ?: ""
                        failure = fetchError.failure
                    } else if (xml == null) {
                        resultList.add(
                            entry(
                                R.string.connection,
                                true,
                                host.toString(),
                                R.string.get_content_error
                            )
                        )
                        hasError = true
                        errorTextId = R.string.get_content_error
                        failure = EnigmaFailure.Parse
                    }
                } else {
                    resultList.add(
                        entry(
                            R.string.port,
                            true,
                            port.toString(),
                            R.string.port_out_of_range
                        )
                    )
                    hasError = true
                    errorTextId = R.string.port_out_of_range
                    failure = EnigmaFailure.Unreachable(
                        EnigmaFailure.UnreachableReason.IllegalHost,
                        port.toString()
                    )
                }
            } else {
                resultList.add(
                    entry(R.string.host, true, host.toString(), R.string.illegal_host)
                )
                hasError = true
                errorTextId = R.string.illegal_host
                failure = EnigmaFailure.Unreachable(
                    EnigmaFailure.UnreachableReason.IllegalHost,
                    host.toString()
                )
            }
        }

        return ProfileCheckResult(
            hasError = hasError,
            isSoftError = isSoftError,
            errorTextId = errorTextId,
            errorTextExt = errorTextExt,
            entries = resultList,
            failure = failure
        )
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

    private fun entry(
        checkTypeId: Int,
        hasError: Boolean,
        value: String?,
        errorTextId: Int = -1,
        errorTextExt: String? = null
    ): ProfileCheckEntry = ProfileCheckEntry(
        hasError = hasError,
        what = checkTypeId,
        value = value.toString(),
        errorTextId = errorTextId,
        errorTextExt = errorTextExt
    )
}
