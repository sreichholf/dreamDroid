package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

@RunWith(AndroidJUnit4::class)
class DeviceInfoParserTest {
    @Test
    fun parsesDeviceinfoFixture() {
        val info = DeviceInfoParser.parse(loadWebFixture("deviceinfo.xml"))
        assertNotNull(info)
        assertFalse(info!!.isEmpty())

        assertEquals("2016-07-28-vti-master (d75da0c)", info.guiVersion)
        assertEquals("9.0.3.", info.imageVersion)
        assertEquals("1.7.4", info.interfaceVersion)
        assertEquals("0", info.frontProcessorVersion)
        assertEquals("Solo4K", info.deviceName)

        assertEquals(2, info.frontends.size)
        assertEquals("Tuner A", info.frontends[0].name)
        assertTrue(info.frontends[0].model.contains("DVB-S2"))
        assertEquals("Tuner B", info.frontends[1].name)

        assertEquals(1, info.nics.size)
        assertEquals("eth0", info.nics[0].name)
        assertEquals("192.168.0.8", info.nics[0].ip)
        assertEquals("00:1d:ec:0a:be:ae", info.nics[0].mac)

        assertEquals(1, info.hdds.size)
        assertEquals("ATA(ST2000LM003 HN-M)", info.hdds[0].model)
        assertEquals("1.82 TB", info.hdds[0].capacity)
        assertEquals("1405.121 GB", info.hdds[0].free)
    }

    @Test
    fun emptyXmlYieldsNull() {
        assertNull(DeviceInfoParser.parse(""))
    }

    @Test
    fun malformedXmlYieldsNull() {
        assertNull(DeviceInfoParser.parse("<e2deviceinfo><e2devicename>Solo"))
    }

    @Test
    fun emptyDeviceInfoElementYieldsNull() {
        assertNull(
            DeviceInfoParser.parse(
                """<?xml version="1.0" encoding="UTF-8"?><e2deviceinfo></e2deviceinfo>""",
            ),
        )
    }

    @Test
    fun deviceInfoSerializableRoundTrip() {
        val info = DeviceInfoParser.parse(loadWebFixture("deviceinfo.xml"))
        assertNotNull(info)

        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(info) }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use {
            it.readObject() as DeviceInfo
        }
        assertEquals(info, restored)
        assertEquals("Solo4K", restored.deviceName)
        assertEquals(2, restored.frontends.size)
    }
}
