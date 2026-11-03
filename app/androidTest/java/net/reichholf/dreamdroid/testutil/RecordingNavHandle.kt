package net.reichholf.dreamdroid.testutil

import java.lang.reflect.Proxy
import net.reichholf.dreamdroid.ui.nav.Epg
import net.reichholf.dreamdroid.ui.nav.MultiEpg
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

/**
 * A [PhoneNavHandle] that records the EPG routes a destination asks for. Any other call
 * fails the test; Compose compares parameters with equals when it decides whether to skip,
 * so equals, hashCode, and toString work.
 */
fun recordingNavHandle(
    onEpg: (Epg) -> Unit = {},
    onMultiEpg: (MultiEpg) -> Unit = {}
): PhoneNavHandle = Proxy.newProxyInstance(
    PhoneNavHandle::class.java.classLoader,
    arrayOf(PhoneNavHandle::class.java)
) { proxy, method, args ->
    val callArgs = args.orEmpty()
    when (method.name) {
        "navigateToEpg" -> {
            onEpg(
                Epg(
                    serviceRef = callArgs[0] as String? ?: "",
                    serviceName = callArgs[1] as String? ?: "",
                    timeSec = callArgs[2] as Long? ?: 0L,
                    tabRootRef = callArgs[3] as String? ?: ""
                )
            )
            true
        }

        "navigateToMultiEpg" -> {
            onMultiEpg(
                MultiEpg(
                    serviceRef = callArgs[0] as String? ?: "",
                    serviceName = callArgs[1] as String? ?: "",
                    focusedServiceRef = callArgs[2] as String? ?: "",
                    timeSec = callArgs[3] as Long? ?: 0L,
                    tabRootRef = callArgs[4] as String? ?: ""
                )
            )
            true
        }

        "equals" -> proxy === args?.firstOrNull()

        "hashCode" -> System.identityHashCode(proxy)

        "toString" -> "recordingNavHandle"

        else -> error("unexpected PhoneNavHandle.${method.name}")
    }
} as PhoneNavHandle
