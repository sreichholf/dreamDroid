package net.reichholf.dreamdroid.ui.nav

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalMaterial3Api::class)
class ShellChromeScrollStateTest {
    /** Both bars fully slid out: a 64px top bar and 136px of bottom chrome. */
    private fun hiddenBars() = ShellChromeScrollState().apply {
        topBar.heightOffsetLimit = -64f
        topBar.heightOffset = -64f
        bottomBar.heightOffsetLimit = -136f
        bottomBar.heightOffset = -136f
    }

    @Test
    fun revealBottomLeavesTopBarHidden() {
        val state = hiddenBars()
        state.revealBottom()
        assertEquals(0f, state.bottomBar.heightOffset)
        assertEquals(-64f, state.topBar.heightOffset)
    }

    @Test
    fun revealAllShowsTopBarToo() {
        val state = hiddenBars()
        state.revealAll()
        assertEquals(0f, state.bottomBar.heightOffset)
        assertEquals(0f, state.topBar.heightOffset)
    }

    @Test
    fun screenShownForTheFirstTimeShowsAllBars() {
        val state = hiddenBars()
        state.restoreFrom(SavedStateHandle())
        assertEquals(0f, state.topBar.heightOffset)
        assertEquals(0f, state.bottomBar.heightOffset)
    }

    @Test
    fun backFindsTheBarsWhereTheScreenLeftThem() {
        val list = SavedStateHandle()
        val state = hiddenBars()
        state.saveTo(list)
        // A detail screen opens with all bars shown, then Back returns to the list.
        state.restoreFrom(SavedStateHandle())
        state.restoreFrom(list)
        assertEquals(-64f, state.topBar.heightOffset)
        assertEquals(-136f, state.bottomBar.heightOffset)
    }
}
