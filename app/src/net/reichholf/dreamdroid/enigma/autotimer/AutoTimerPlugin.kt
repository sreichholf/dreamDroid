package net.reichholf.dreamdroid.enigma.autotimer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.SimpleResultParser
import net.reichholf.dreamdroid.enigma.simpleResultFromFetch
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * The AutoTimer plugin's web API, by its `api_version`. Two plugins exist: opendreambox's
 * (1.6) and oe-alliance's (1.7). Either can sit behind either web interface.
 */
enum class AutoTimerApi {
    /**
     * api_version 1.6 and older: a run writes `<ignore />` every 50 s and ends with its
     * summary; a failing preview ends with `<exception>`.
     */
    V1_6,

    /**
     * api_version 1.7 and newer: a run answers only once it is done, with nothing in between,
     * and a failing preview never answers (`AutoTimerResource.py:44-56,121-180` there).
     */
    V1_7;

    /** Whether "run now" can wait for the plugin's summary. */
    val runAnswersInTime: Boolean
        get() = this == V1_6

    companion object {
        /** [apiVersion] as `major.minor`; anything unreadable is the older API. */
        fun of(apiVersion: String?): AutoTimerApi {
            val parts = apiVersion?.trim()?.split('.').orEmpty()
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return V1_6
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return if (major > 1 || (major == 1 && minor >= 7)) V1_7 else V1_6
        }
    }
}

/** Whether the receiver has the AutoTimer plugin, and which API it speaks. */
sealed interface AutoTimerPlugin {
    data object Missing : AutoTimerPlugin

    data class Installed(val api: AutoTimerApi) : AutoTimerPlugin
}

/**
 * The AutoTimer plugin's own XML under `/autotimer`, which both web interfaces serve: the
 * Dreambox one through `/web/external`, OpenWebif through its `ATController` (`AT.py:140-195`
 * at e46534f). Its requests depend on the plugin, not on the web interface, except the enable
 * switch: only the OpenWebif client sends it to [change]. [fetch] is the web interface client's
 * request.
 */
internal class AutoTimerPluginApi(
    private val fetch: (String, List<NameValuePair>) -> EnigmaHttpResult
) {
    /**
     * The plugin's list. A config the box cannot load comes back as a simple result; its text
     * becomes a [EnigmaFailure.BoxRejected].
     */
    suspend fun list(): EnigmaResponse<AutoTimerList> = withContext(Dispatchers.IO) {
        when (val fetched = fetch(URIStore.AUTOTIMER_LIST, emptyList())) {
            is EnigmaHttpResult.Success -> AutoTimerListParser.parse(fetched.text)
                ?.let { EnigmaResponse(it) }
                ?: EnigmaResponse(null, rejection(fetched.text))

            is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)
        }
    }

    /**
     * `/autotimer/get`, the plugin's settings with its `api_version` (`AutoTimerResource.py`,
     * 1.6 and 1.7). A 404 means the plugin is missing: OpenWebif mounts the page only when the
     * plugin imports (`AT.py:144-156`). The settings' texts are not XML-escaped in 1.7, so the
     * version is read from the raw text.
     */
    suspend fun plugin(): EnigmaResponse<AutoTimerPlugin> = withContext(Dispatchers.IO) {
        when (val fetched = fetch(URIStore.AUTOTIMER_SETTINGS, emptyList())) {
            is EnigmaHttpResult.Success -> {
                val text = fetched.text
                if (SETTINGS_ROOT in text) {
                    val version = API_VERSION.find(text)?.groupValues?.get(1)
                    EnigmaResponse(AutoTimerPlugin.Installed(AutoTimerApi.of(version)))
                } else {
                    EnigmaResponse(null, EnigmaHttpError(EnigmaFailure.Parse))
                }
            }

            is EnigmaHttpResult.Failure -> {
                val failure = fetched.error.failure
                if (failure is EnigmaFailure.Http && failure.code == HTTP_NOT_FOUND) {
                    EnigmaResponse(AutoTimerPlugin.Missing)
                } else {
                    EnigmaResponse(null, fetched.error)
                }
            }
        }
    }

    suspend fun test(id: AutoTimerId): EnigmaResponse<PreviewOutcome> =
        withContext(Dispatchers.IO) {
            when (val fetched = fetch(URIStore.AUTOTIMER_TEST, idParams(id))) {
                is EnigmaHttpResult.Success ->
                    EnigmaResponse(AutoTimerPreviewParser.parse(fetched.text))

                is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)
            }
        }

    /** `/autotimer/edit` with the changed field groups; see [autoTimerEditParams]. */
    suspend fun edit(write: AutoTimerWrite): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.AUTOTIMER_EDIT, autoTimerEditParams(write))

    /**
     * `/autotimer/change`, which sets only `name` and `enabled` (`AutoTimerResource.py:511-549`
     * in 1.7). Only OpenWebif mounts it, and only for a plugin that has it (`AT.py:165-170`);
     * otherwise it answers 404.
     */
    suspend fun change(id: AutoTimerId, enabled: Boolean): EnigmaResponse<SimpleResult> =
        simpleResult(
            URIStore.AUTOTIMER_CHANGE,
            idParams(id) + NameValuePair("enabled", if (enabled) "1" else "0")
        )

    suspend fun remove(id: AutoTimerId): EnigmaResponse<SimpleResult> =
        simpleResult(URIStore.AUTOTIMER_REMOVE, idParams(id))

    /**
     * Runs all enabled AutoTimers; the reply is the plugin's summary. 1.6 writes `<ignore />`
     * every 50 s while it searches, which the parser skips; 1.7 writes nothing until done.
     */
    suspend fun run(): EnigmaResponse<SimpleResult> = simpleResult(URIStore.AUTOTIMER_PARSE)

    private suspend fun simpleResult(
        uri: String,
        params: List<NameValuePair> = emptyList()
    ): EnigmaResponse<SimpleResult> = withContext(Dispatchers.IO) {
        simpleResultFromFetch(fetch(uri, params), SimpleResultParser::parse)
    }

    private fun idParams(id: AutoTimerId) = listOf(NameValuePair("id", id.value.toString()))

    private fun rejection(xml: String): EnigmaHttpError {
        val text = SimpleResultParser.parse(xml)?.stateText
        return EnigmaHttpError(
            if (text.isNullOrBlank()) EnigmaFailure.Parse else EnigmaFailure.BoxRejected(text)
        )
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val SETTINGS_ROOT = "<e2settings"
        val API_VERSION = Regex(
            "<e2settingname>\\s*api_version\\s*</e2settingname>\\s*" +
                "<e2settingvalue>\\s*([^<]*?)\\s*</e2settingvalue>"
        )
    }
}
