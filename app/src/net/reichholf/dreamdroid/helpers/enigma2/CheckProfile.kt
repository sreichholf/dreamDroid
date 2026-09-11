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
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

object CheckProfile {
    const val LOG_TAG: String = "CheckProfile"

    @JvmField
    val FEATURE_EPGNOWNEXT_VERSION: IntArray = intArrayOf(1, 7, 0)

    @JvmField
    val FEATURE_POST_REQEUEST: IntArray = intArrayOf(1, 7, 3)

    const val KEY_HAS_ERROR: String = "error"
    const val KEY_SOFT_ERROR: String = "soft_error"
    const val KEY_VALUE: String = "value"
    const val KEY_ERROR_TEXT: String = "text"
    const val KEY_ERROR_TEXT_EXT: String = "text_ext"
    const val KEY_WHAT: String = "what"
    const val KEY_RESULT_LIST: String = "list"

    @JvmField
    val REQUIRED_VERSION: IntArray = intArrayOf(1, 6, 5)

    @JvmField
    var CURRENT_VERSION: IntArray = intArrayOf(0, 0, 0)

    @JvmStatic
    fun checkProfile(profile: Profile, context: Context): ExtendedHashMap {
        CURRENT_VERSION = intArrayOf(0, 0, 0)
        DreamDroid.disableSleepTimer()
        DreamDroid.disableNowNext()

        val resultList = ArrayList<ExtendedHashMap>()
        val checkResult = ExtendedHashMap()

        checkResult.put(KEY_RESULT_LIST, resultList)
        setError(checkResult, false, -1)

        val host = profile.host

        if (host != null) {
            if (!host.contains(" ")) {
                addEntry(resultList, R.string.host, false, host)

                val port = profile.port
                if (port > 0 && port <= 65535) {
                    addEntry(resultList, R.string.port, false, port.toString())
                    val shc = SimpleHttpClient.getInstance(profile)
                    var xml = profile.cachedDeviceInfo
                    if (xml == null) {
                        xml = Request.get(shc, URIStore.DEVICE_INFO)
                    }

                    if (xml != null && !shc.hasError()) {
                        val deviceInfo = DeviceInfoParser.parse(xml)

                        if (deviceInfo != null && !deviceInfo.isEmpty()) {
                            profile.cachedDeviceInfo = xml
                            addEntry(
                                resultList,
                                R.string.device_name,
                                false,
                                deviceInfo.deviceName,
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

                                addEntry(resultList, R.string.interface_version, false, version)
                            } else {
                                addEntry(
                                    resultList,
                                    R.string.interface_version,
                                    true,
                                    version,
                                    R.string.version_too_low,
                                )
                                setError(checkResult, true, true, R.string.version_too_low)
                            }
                        } else {
                            profile.cachedDeviceInfo = null
                            addEntry(
                                resultList,
                                R.string.connection,
                                true,
                                host.toString(),
                                R.string.get_content_error,
                            )
                            setError(checkResult, true, R.string.get_content_error)
                        }
                    } else if (shc.hasError()) {
                        addEntry(
                            resultList,
                            R.string.connection,
                            true,
                            host.toString(),
                            R.string.connection_error,
                            shc.getErrorText(context),
                        )
                        setError(
                            checkResult,
                            true,
                            R.string.connection_error,
                            shc.getErrorText(context),
                        )
                    } else if (xml == null) {
                        addEntry(
                            resultList,
                            R.string.connection,
                            true,
                            host.toString(),
                            R.string.get_content_error,
                        )
                        setError(checkResult, true, R.string.get_content_error)
                    }
                } else {
                    addEntry(
                        resultList,
                        R.string.port,
                        true,
                        port.toString(),
                        R.string.port_out_of_range,
                    )
                    setError(checkResult, true, R.string.port_out_of_range)
                }
            } else {
                addEntry(
                    resultList,
                    R.string.host,
                    true,
                    host.toString(),
                    R.string.illegal_host,
                )
                setError(checkResult, true, R.string.illegal_host)
            }
        }

        return checkResult
    }

    @JvmStatic
    fun checkVersion(version: String): Int = checkVersion(version, REQUIRED_VERSION)

    @JvmStatic
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

    private fun addEntry(
        resultList: ArrayList<ExtendedHashMap>,
        checkTypeId: Int,
        hasError: Boolean,
        value: String?,
        errorTextId: Int,
    ) {
        addEntry(resultList, checkTypeId, hasError, value, errorTextId, null)
    }

    private fun addEntry(
        resultList: ArrayList<ExtendedHashMap>,
        checkTypeId: Int,
        hasError: Boolean,
        value: String?,
        errorTextId: Int,
        errorTextExt: String?,
    ) {
        val entry = ExtendedHashMap()
        entry.put(KEY_HAS_ERROR, hasError)
        entry.put(KEY_WHAT, checkTypeId)
        entry.put(KEY_VALUE, value.toString())
        entry.put(KEY_ERROR_TEXT, errorTextId)
        entry.put(KEY_ERROR_TEXT_EXT, errorTextExt)
        resultList.add(entry)
    }

    private fun addEntry(
        resultList: ArrayList<ExtendedHashMap>,
        checkTypeId: Int,
        hasError: Boolean,
        value: String?,
    ) {
        addEntry(resultList, checkTypeId, hasError, value, -1)
    }

    private fun setError(checkResult: ExtendedHashMap, hasError: Boolean, errorTextId: Int) {
        setError(checkResult, hasError, false, errorTextId, null)
    }

    private fun setError(
        checkResult: ExtendedHashMap,
        hasError: Boolean,
        errorTextId: Int,
        extendedText: String?,
    ) {
        setError(checkResult, hasError, false, errorTextId, extendedText)
    }

    private fun setError(
        checkResult: ExtendedHashMap,
        hasError: Boolean,
        isSoftError: Boolean,
        errorTextId: Int,
    ) {
        setError(checkResult, hasError, isSoftError, errorTextId, null)
    }

    private fun setError(
        checkResult: ExtendedHashMap,
        hasError: Boolean,
        isSoftError: Boolean,
        errorTextId: Int,
        extendedText: String?,
    ) {
        var text = extendedText
        checkResult.put(KEY_HAS_ERROR, hasError)
        checkResult.put(KEY_SOFT_ERROR, isSoftError)
        checkResult.put(KEY_ERROR_TEXT, errorTextId)
        if (text == null) {
            text = ""
        }
        checkResult.put(KEY_ERROR_TEXT_EXT, text)
    }
}
