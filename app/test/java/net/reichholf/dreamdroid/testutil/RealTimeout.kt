package net.reichholf.dreamdroid.testutil

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * [withTimeout] on the wall clock. Under `runTest`, a plain [withTimeout] counts virtual
 * time, which jumps ahead while the test waits on real threads (MockWebServer, OkHttp), so
 * the bound fires before the awaited signal arrives.
 */
suspend fun <T> withRealTimeout(millis: Long, block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(millis, block) }
