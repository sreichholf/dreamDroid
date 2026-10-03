package net.reichholf.dreamdroid.enigma

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult

/**
 * Tells which web interface a receiver runs, from the `/web/deviceinfo` answer the profile
 * check already has. It belongs to neither client: it is the only code that knows both
 * formats. Built per check by [ReceiverApiFactory].
 *
 * OpenWebif reports `e2webifversion` as `OWIF <version>`; one `/api/statusinfo` request
 * confirms it. A Dreambox pays no extra request.
 */
class ReceiverDetector(private val http: EnigmaHttp) {
    /**
     * The flavor of the receiver that answered [deviceInfo]. No value when the answers
     * conflict: `deviceinfo` says OpenWebif but `/api/statusinfo` answers with no JSON object.
     * The error when `/api/statusinfo` got no answer at all.
     */
    suspend fun detect(deviceInfo: DeviceInfo): EnigmaResponse<ReceiverFlavor> {
        if (!deviceInfo.interfaceVersion.startsWith(OPEN_WEBIF_VERSION_PREFIX)) {
            return EnigmaResponse(ReceiverFlavor.DreamboxWebIf)
        }
        return when (val answer = withContext(Dispatchers.IO) { http.fetch(STATUS_INFO) }) {
            is EnigmaHttpResult.Success -> EnigmaResponse(
                ReceiverFlavor.OpenWebif.takeIf { isJsonObject(answer.text) }
            )

            // An HTTP error status is an answer, though not OpenWebif's.
            is EnigmaHttpResult.Failure ->
                if (answer.error.failure.let {
                        it is EnigmaFailure.Http || it is EnigmaFailure.Auth
                    }
                ) {
                    EnigmaResponse(null)
                } else {
                    EnigmaResponse(null, answer.error)
                }
        }
    }

    private fun isJsonObject(text: String): Boolean = try {
        JsonParser.parseString(text).isJsonObject
    } catch (_: JsonParseException) {
        false
    }

    private companion object {
        const val OPEN_WEBIF_VERSION_PREFIX = "OWIF "
        const val STATUS_INFO = "/api/statusinfo"
    }
}
