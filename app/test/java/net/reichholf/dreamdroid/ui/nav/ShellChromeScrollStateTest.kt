package net.reichholf.dreamdroid.ui.nav

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellChromeScrollStateTest {
    private fun stateWithChrome(heightPx: Float = 200f) = ShellChromeScrollState().apply {
        bottomHeightPx = heightPx
    }

    @Test
    fun scrollingDownSlidesBottomChromeOutAndCollapsesFab() {
        val state = stateWithChrome()
        state.onScroll(-50f)
        assertEquals(0.25f, state.bottomHiddenFraction)
        assertFalse(state.fabExpanded)
        state.onScroll(-500f)
        assertEquals(1f, state.bottomHiddenFraction)
    }

    @Test
    fun scrollingUpBringsBottomChromeBackAndExpandsFab() {
        val state = stateWithChrome()
        state.onScroll(-500f)
        state.onScroll(80f)
        assertEquals(0.6f, state.bottomHiddenFraction, 0.0001f)
        assertTrue(state.fabExpanded)
        state.onScroll(500f)
        assertEquals(0f, state.bottomHiddenFraction)
    }

    @Test
    fun hiddenChromeStaysHiddenWhenItGrows() {
        val state = stateWithChrome(heightPx = 80f)
        state.onScroll(-500f)
        // The now-playing strip turned on below the hidden destination bar.
        state.bottomHeightPx = 136f
        assertEquals(1f, state.bottomHiddenFraction)
        state.onScroll(34f)
        assertEquals(0.75f, state.bottomHiddenFraction)
    }

    @Test
    fun revealBottomLeavesTopBarHidden() {
        val state = stateWithChrome()
        state.topBar.heightOffsetLimit = -64f
        state.topBar.heightOffset = -64f
        state.onScroll(-500f)
        state.revealBottom()
        assertEquals(0f, state.bottomHiddenFraction)
        assertTrue(state.fabExpanded)
        assertEquals(-64f, state.topBar.heightOffset)
    }

    @Test
    fun revealAllShowsTopBarToo() {
        val state = stateWithChrome()
        state.topBar.heightOffsetLimit = -64f
        state.topBar.heightOffset = -64f
        state.onScroll(-500f)
        state.revealAll()
        assertEquals(0f, state.bottomHiddenFraction)
        assertEquals(0f, state.topBar.heightOffset)
    }

    @Test
    fun screenShownForTheFirstTimeShowsAllBars() {
        val state = stateWithChrome()
        state.topBar.heightOffsetLimit = -64f
        state.topBar.heightOffset = -64f
        state.onScroll(-500f)
        state.restoreFrom(SavedStateHandle())
        assertEquals(0f, state.topBar.heightOffset)
        assertEquals(0f, state.bottomHiddenFraction)
        assertTrue(state.fabExpanded)
    }

    @Test
    fun backFindsTheBarsWhereTheScreenLeftThem() {
        val list = SavedStateHandle()
        val state = stateWithChrome()
        state.topBar.heightOffsetLimit = -64f
        state.topBar.heightOffset = -64f
        state.onScroll(-500f)
        state.saveTo(list)
        // A detail screen opens with all bars shown, then Back returns to the list.
        state.restoreFrom(SavedStateHandle())
        state.restoreFrom(list)
        assertEquals(-64f, state.topBar.heightOffset)
        assertEquals(1f, state.bottomHiddenFraction)
        assertFalse(state.fabExpanded)
    }

    @Test
    fun connectionLetsContentScrollAndStopsWhenDisabled() {
        val state = stateWithChrome()
        var enabled = true
        val connection = state.bottomConnection { enabled }
        val consumed = connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, consumed)
        assertEquals(0.2f, state.bottomHiddenFraction)
        enabled = false
        connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(0.2f, state.bottomHiddenFraction)
    }

    @Test
    fun settleSnapsToTheNearerEnd() = runTest {
        val mostlyShown = stateWithChrome()
        mostlyShown.onScroll(-80f)
        val mostlyHidden = stateWithChrome()
        mostlyHidden.onScroll(-120f)
        withContext(FakeFrameClock()) {
            mostlyShown.settleBottom()
            mostlyHidden.settleBottom()
        }
        assertEquals(0f, mostlyShown.bottomHiddenFraction)
        assertEquals(1f, mostlyHidden.bottomHiddenFraction)
    }

    @Test
    fun revealDuringSettleWins() = runTest {
        val state = stateWithChrome()
        state.onScroll(-150f)
        val clock = FakeFrameClock(beforeFrame = { frame ->
            if (frame == 3) {
                state.revealBottom()
            }
        })
        withContext(clock) { state.settleBottom() }
        assertEquals(0f, state.bottomHiddenFraction)
    }
}

/** Frames 16ms apart; [beforeFrame] runs before each frame with its 1-based number. */
private class FakeFrameClock(private val beforeFrame: (Int) -> Unit = {}) : MonotonicFrameClock {
    private var frame = 0

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        frame += 1
        beforeFrame(frame)
        return onFrame(frame * 16_000_000L)
    }
}
