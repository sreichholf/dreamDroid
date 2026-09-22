package net.reichholf.dreamdroid.ui.device

import net.reichholf.dreamdroid.enigma.DeviceFrontend
import net.reichholf.dreamdroid.enigma.DeviceHdd
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.DeviceNic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceInfoSavedTest {
    @Test
    fun emptyAccessReadsNullInfoAndNotReady() {
        val saved = readDeviceInfoSaved(MapDeviceInfoSavedAccess())
        assertNull(saved.info)
        assertFalse(saved.ready)
        assertTrue(shouldLoadDeviceInfo(saved.info))
    }

    @Test
    fun populatedInfoRoundTripsWithReady() {
        val info = DeviceInfo(
            guiVersion = "2016-07-28",
            deviceName = "Solo4K",
            frontends = listOf(DeviceFrontend(name = "Tuner A", model = "DVB-S2")),
            nics = listOf(
                DeviceNic(name = "eth0", mac = "00:1d:ec:0a:be:ae", ip = "192.168.0.8")
            ),
            hdds = listOf(DeviceHdd(model = "ATA", capacity = "1.82 TB", free = "1.00 TB"))
        )
        val access = MapDeviceInfoSavedAccess()
        DeviceInfoSaved(info = info, ready = true).writeTo(access)
        val saved = readDeviceInfoSaved(access)
        assertEquals(info, saved.info)
        assertTrue(saved.ready)
        assertFalse(shouldLoadDeviceInfo(saved.info))
    }

    @Test
    fun nullInfoRemovesKeyAndStoresFalseReady() {
        val values = mutableMapOf<String, Any>(
            DeviceInfoSavedKeys.INFO to DeviceInfo(deviceName = "Solo4K"),
            DeviceInfoSavedKeys.READY to true
        )
        val access = MapDeviceInfoSavedAccess(values)
        DeviceInfoSaved(info = null, ready = false).writeTo(access)
        assertFalse(values.containsKey(DeviceInfoSavedKeys.INFO))
        assertTrue(values.containsKey(DeviceInfoSavedKeys.READY))
        assertEquals(false, values[DeviceInfoSavedKeys.READY])
        val saved = readDeviceInfoSaved(access)
        assertNull(saved.info)
        assertFalse(saved.ready)
        assertTrue(shouldLoadDeviceInfo(saved.info))
    }

    @Test
    fun defaultDeviceInfoStillShouldLoad() {
        val info = DeviceInfo()
        assertTrue(info.isEmpty())
        assertTrue(shouldLoadDeviceInfo(info))
    }
}
