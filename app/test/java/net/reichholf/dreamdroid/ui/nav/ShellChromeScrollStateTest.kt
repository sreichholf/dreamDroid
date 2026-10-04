package net.reichholf.dreamdroid.ui.nav

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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
        assertEquals(50f, state.bottomOffsetPx)
        assertFalse(state.fabExpanded)
        state.onScroll(-500f)
        assertEquals(200f, state.bottomOffsetPx)
    }

    @Test
    fun scrollingUpBringsBottomChromeBackAndExpandsFab() {
        val state = stateWithChrome()
        state.onScroll(-500f)
        state.onScroll(80f)
        assertEquals(120f, state.bottomOffsetPx)
        assertTrue(state.fabExpanded)
        state.onScroll(500f)
        assertEquals(0f, state.bottomOffsetPx)
    }

    @Test
    fun screenWithoutBottomChromeOnlyCollapsesFab() {
        val state = stateWithChrome(heightPx = 0f)
        state.onScroll(-50f)
        assertEquals(0f, state.bottomOffsetPx)
        assertFalse(state.fabExpanded)
    }

    @Test
    fun shrunkChromeComesBackFromItsNewHeight() {
        val state = stateWithChrome(heightPx = 200f)
        state.onScroll(-500f)
        // The now-playing strip was turned off: only the destination bar is left.
        state.bottomHeightPx = 80f
        state.onScroll(30f)
        assertEquals(50f, state.bottomOffsetPx)
    }

    @Test
    fun revealBottomLeavesTopBarHidden() {
        val state = stateWithChrome()
        state.topBar.heightOffsetLimit = -64f
        state.topBar.heightOffset = -64f
        state.onScroll(-500f)
        state.revealBottom()
        assertEquals(0f, state.bottomOffsetPx)
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
        assertEquals(0f, state.bottomOffsetPx)
        assertEquals(0f, state.topBar.heightOffset)
    }

    @Test
    fun connectionLetsContentScrollAndStopsWhenDisabled() {
        val state = stateWithChrome()
        var enabled = true
        val connection = state.bottomConnection { enabled }
        val consumed = connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, consumed)
        assertEquals(40f, state.bottomOffsetPx)
        enabled = false
        connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(40f, state.bottomOffsetPx)
    }
}
