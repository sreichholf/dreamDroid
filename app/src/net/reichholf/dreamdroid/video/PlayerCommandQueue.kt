package net.reichholf.dreamdroid.video

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Runs blocking calls one at a time, in the order they were posted, on [dispatcher].
 *
 * libVLC blocks a caller while it closes a stream: `setMedia`, `stop`, and `release` join the
 * old input thread, which can sit in an HTTP read for as long as the receiver keeps the old
 * connection open. Getters and setters wait on the same input lock. Posting every call here
 * keeps the main thread out of those waits.
 */
class PlayerCommandQueue(dispatcher: CoroutineDispatcher) {
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            for (command in commands) {
                val started = System.nanoTime()
                try {
                    command()
                } catch (t: Throwable) {
                    // One failed call must not stop the queue; later calls would never run.
                    Log.w(LOG_TAG, "Player command failed", t)
                }
                val tookMs = (System.nanoTime() - started) / 1_000_000
                if (tookMs >= SLOW_COMMAND_MS) {
                    // Would have been an ANR on the main thread.
                    Log.w(LOG_TAG, "Player command took $tookMs ms")
                }
            }
        }
    }

    /** Queues [command] and returns at once. */
    fun post(command: () -> Unit) {
        commands.trySend(command)
    }

    /** Queues [command] and suspends until it has run. */
    suspend fun <T> call(command: () -> T): T {
        val result = CompletableDeferred<T>()
        post {
            try {
                result.complete(command())
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
        return result.await()
    }

    private companion object {
        const val LOG_TAG = "PlayerCommandQueue"
        const val SLOW_COMMAND_MS = 5_000L
    }
}
