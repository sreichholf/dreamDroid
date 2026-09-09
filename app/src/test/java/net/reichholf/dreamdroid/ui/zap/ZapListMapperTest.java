package net.reichholf.dreamdroid.ui.zap;

import net.reichholf.dreamdroid.enigma.Service;
import net.reichholf.dreamdroid.enigma.ServiceParser;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ZapListMapperTest {
    @Test
    public void rowsFromDropsMarkersAndKeepsChannels() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/getservices.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        List<Service> parsed = ServiceParser.INSTANCE.parse(xml);
        assertEquals(3, parsed.size());

        List<Service> rows = ZapListMapper.rowsFrom(parsed);
        assertEquals(2, rows.size());
        assertEquals("Favourites (TV)", rows.get(0).getName());
        assertEquals("Das Erste HD", rows.get(1).getName());
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", rows.get(1).getReference());
        for (Service row : rows) {
            assertTrue(!net.reichholf.dreamdroid.helpers.enigma2.Service.isMarker(row.getReference()));
        }
    }

    @Test
    public void rowsFromEmptyListIsEmpty() {
        assertEquals(0, ZapListMapper.rowsFrom(Collections.emptyList()).size());
    }

    @Test
    public void bouquetFromReadsReferenceAndName() {
        ExtendedHashMap map = new ExtendedHashMap();
        map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE, "1:7:1:0:0:0:0:0:0:0:");
        map.put(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME, "Favourites (TV)");
        Service bouquet = ZapListMapper.bouquetFrom(map);
        assertEquals("1:7:1:0:0:0:0:0:0:0:", bouquet.getReference());
        assertEquals("Favourites (TV)", bouquet.getName());
    }

    @Test
    public void bouquetFromNullMapIsEmptyService() {
        Service bouquet = ZapListMapper.bouquetFrom(null);
        assertEquals("", bouquet.getReference());
        assertEquals("", bouquet.getName());
    }

    @Test
    public void toBouquetMapWritesReferenceAndName() {
        Service service = new Service("1:7:1:0:0:0:0:0:0:0:", "Favourites (TV)");
        ExtendedHashMap map = ZapListMapper.toBouquetMap(service);
        assertEquals("1:7:1:0:0:0:0:0:0:0:", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE));
        assertEquals("Favourites (TV)", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME));
        assertEquals(service.getReference(), ZapListMapper.bouquetFrom(map).getReference());
        assertEquals(service.getName(), ZapListMapper.bouquetFrom(map).getName());
    }

    @Test
    public void toBouquetMapNullIsEmptyKeys() {
        ExtendedHashMap map = ZapListMapper.toBouquetMap(null);
        assertEquals("", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE));
        assertEquals("", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_NAME));
    }
}
