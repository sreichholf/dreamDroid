package net.reichholf.dreamdroid.testutil

import java.lang.reflect.Proxy
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

/**
 * A [PhoneNavHandle] for a destination whose test never navigates: any call fails the
 * test. Compose still compares parameters with equals when it decides whether to skip,
 * so equals, hashCode, and toString work.
 */
fun unusedNavHandle(): PhoneNavHandle = Proxy.newProxyInstance(
    PhoneNavHandle::class.java.classLoader,
    arrayOf(PhoneNavHandle::class.java)
) { proxy, method, args ->
    when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "unusedNavHandle"
        else -> error("unexpected PhoneNavHandle.${method.name}")
    }
} as PhoneNavHandle
