package net.reichholf.dreamdroid.video

import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PlayerCommandQueueTest {
    private val queue = PlayerCommandQueue(Dispatchers.IO)

    @Test
    fun postReturnsWhileACommandBlocksAndLaterCommandsWaitTheirTurn() = runBlocking {
        // Stands in for setMedia joining a stream that is stuck in a read.
        val closing = CountDownLatch(1)
        val ran = Collections.synchronizedList(mutableListOf<String>())

        queue.post {
            closing.await()
            ran += "setMedia"
        }
        queue.post { ran += "play" }

        assertEquals(emptyList<String>(), ran.toList())
        closing.countDown()
        withTimeout(TIMEOUT_MS) { queue.call { } }
        assertEquals(listOf("setMedia", "play"), ran.toList())
    }

    @Test
    fun callReturnsTheCommandsResultInOrder() = runBlocking {
        val ran = Collections.synchronizedList(mutableListOf<Int>())
        queue.post { ran += 1 }

        val result = withTimeout(TIMEOUT_MS) {
            queue.call {
                ran += 2
                ran.toList()
            }
        }

        assertEquals(listOf(1, 2), result)
    }

    @Test
    fun aFailingCommandDoesNotStopTheQueue() = runBlocking {
        queue.post { error("released") }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { withTimeout(TIMEOUT_MS) { queue.call { error("released") } } }
        }
        assertEquals("next", withTimeout(TIMEOUT_MS) { queue.call { "next" } })
    }

    @Test
    fun anErrorDoesNotStopTheQueue() = runBlocking {
        queue.post { throw UnsatisfiedLinkError("libvlc") }

        assertEquals("next", withTimeout(TIMEOUT_MS) { queue.call { "next" } })
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
