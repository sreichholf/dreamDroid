package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DeviceInfoParserTest {
    @Test
    public void parsesDeviceinfoFixture() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/deviceinfo.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        DeviceInfo info = DeviceInfoParser.INSTANCE.parse(xml);
        assertNotNull(info);
        assertFalse(info.isEmpty());

        assertEquals("2016-07-28-vti-master (d75da0c)", info.getGuiVersion());
        assertEquals("9.0.3.", info.getImageVersion());
        assertEquals("1.7.4", info.getInterfaceVersion());
        assertEquals("0", info.getFrontProcessorVersion());
        assertEquals("Solo4K", info.getDeviceName());

        assertEquals(2, info.getFrontends().size());
        assertEquals("Tuner A", info.getFrontends().get(0).getName());
        assertTrue(info.getFrontends().get(0).getModel().contains("DVB-S2"));
        assertEquals("Tuner B", info.getFrontends().get(1).getName());

        assertEquals(1, info.getNics().size());
        assertEquals("eth0", info.getNics().get(0).getName());
        assertEquals("192.168.0.8", info.getNics().get(0).getIp());
        assertEquals("00:1d:ec:0a:be:ae", info.getNics().get(0).getMac());

        assertEquals(1, info.getHdds().size());
        assertEquals("ATA(ST2000LM003 HN-M)", info.getHdds().get(0).getModel());
        assertEquals("1.82 TB", info.getHdds().get(0).getCapacity());
        assertEquals("1405.121 GB", info.getHdds().get(0).getFree());
    }

    @Test
    public void emptyXmlYieldsNull() {
        assertNull(DeviceInfoParser.INSTANCE.parse(""));
    }

    @Test
    public void malformedXmlYieldsNull() {
        assertNull(DeviceInfoParser.INSTANCE.parse("<e2deviceinfo><e2devicename>Solo"));
    }

    @Test
    public void emptyDeviceInfoElementYieldsNull() {
        assertNull(DeviceInfoParser.INSTANCE.parse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e2deviceinfo></e2deviceinfo>"));
    }
}
