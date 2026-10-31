package net.reichholf.dreamdroid.video

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstPictureWatchTest {
    private val scope = TestScope(StandardTestDispatcher())
    private val events = mutableListOf<PlayerEvent>()

    private fun watch() = FirstPictureWatch(scope, TIMEOUT_MS) { events += it }

    @Test
    fun radioIsReadyAtOnce() {
        watch().onTracks(hasVideo = false)

        assertEquals(listOf<PlayerEvent>(PlayerEvent.PictureReady), events)
    }

    @Test
    fun videoIsReadyAtItsFirstFrameAndNeverReportsNoVideo() {
        val watch = watch()
        watch.onTracks(hasVideo = true)
        scope.advanceTimeBy(TIMEOUT_MS / 2)
        watch.onFirstFrame()
        watch.onTracks(hasVideo = true)
        scope.advanceUntilIdle()

        assertEquals(listOf<PlayerEvent>(PlayerEvent.PictureReady), events)
    }

    @Test
    fun videoWithoutAFrameReportsNoVideoAfterTheTimeout() {
        watch().onTracks(hasVideo = true)
        scope.advanceTimeBy(TIMEOUT_MS - 1)
        scope.runCurrent()
        assertEquals(emptyList<PlayerEvent>(), events)

        scope.advanceTimeBy(1)
        scope.runCurrent()
        assertEquals(listOf<PlayerEvent>(PlayerEvent.NoVideo), events)
    }

    @Test
    fun aCancelledWatchReportsNothing() {
        val watch = watch()
        watch.onTracks(hasVideo = true)
        watch.cancel()
        scope.advanceUntilIdle()

        assertEquals(emptyList<PlayerEvent>(), events)
    }

    @Test
    fun eachStreamOfAZapDecidesOnItsOwn() {
        val first = watch()
        first.onTracks(hasVideo = true)
        first.onFirstFrame()
        first.cancel()

        watch().onTracks(hasVideo = false)

        val third = watch()
        third.onTracks(hasVideo = true)
        scope.advanceUntilIdle()

        assertEquals(
            listOf(PlayerEvent.PictureReady, PlayerEvent.PictureReady, PlayerEvent.NoVideo),
            events
        )
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
