package net.reichholf.dreamdroid.ui.device

import net.reichholf.dreamdroid.enigma.DeviceInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceInfoUiStateRestoreTest {
    @Test
    fun restoreAppliesSavedInfoWhenUiStateNotReady() {
        val uiState = DeviceInfoUiState()
        val info = DeviceInfo(guiVersion = "2016-07-28", deviceName = "Solo4K")
        assertFalse(uiState.ready)
        restoreDeviceInfoUiState(uiState, info, deviceInfoReady = true) { capacity, free ->
            "$capacity ($free free)"
        }
        assertTrue(uiState.ready)
        assertEquals("Solo4K", uiState.deviceName)
        assertEquals("2016-07-28", uiState.guiVersion)
    }

    @Test
    fun restoreSkipsWhenNotReadyFlag() {
        val uiState = DeviceInfoUiState()
        val info = DeviceInfo(deviceName = "Solo4K")
        restoreDeviceInfoUiState(uiState, info, deviceInfoReady = false) { capacity, free ->
            "$capacity ($free free)"
        }
        assertFalse(uiState.ready)
        assertEquals("", uiState.deviceName)
    }
}
