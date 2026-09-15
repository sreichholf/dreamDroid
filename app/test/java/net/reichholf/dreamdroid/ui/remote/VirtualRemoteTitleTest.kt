package net.reichholf.dreamdroid.ui.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class VirtualRemoteTitleTest {
    @Test
    fun fullPadUsesVirtualRemoteWithoutAppPrefix() {
        val title = virtualRemoteToolbarTitle(
            quickZap = false,
            virtualRemote = "Virtual Remote",
            quickZapLabel = "QuickZap Layout (Simple)"
        )
        assertEquals("Virtual Remote", title)
        assertFalse(title.contains("::"))
        assertFalse(title.contains("DreamDroid"))
    }

    @Test
    fun quickZapUsesLayoutNameWithoutAppPrefix() {
        val title = virtualRemoteToolbarTitle(
            quickZap = true,
            virtualRemote = "Virtual Remote",
            quickZapLabel = "QuickZap Layout (Simple)"
        )
        assertEquals("QuickZap Layout (Simple)", title)
        assertFalse(title.contains("::"))
        assertFalse(title.contains("DreamDroid"))
    }
}
